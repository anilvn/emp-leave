#!/usr/bin/env bash

set -uo pipefail

usage() {
  cat <<'EOF'
Usage: create-user.sh [--base-url URL] [--hr-username USERNAME] [--help]

Creates Sunil, PD, Anil, Jay, PG, and Rajesh sequentially through the HR user API.

Configuration (environment variables):
  BASE_URL             API base URL (default: https://localhost/api)
  HR_USERNAME          HR account username (default: hr.admin)
  HR_PASSWORD          HR account password (required)
  TEMPORARY_PASSWORD   Initial password for each new account (required)

Dependencies:
  bash, curl, and jq. The script checks curl and jq before making API calls.
  For a local self-signed HTTPS certificate, set CURL_INSECURE=1 to pass -k.
EOF
}

BASE_URL=${BASE_URL:-https://localhost/api}
HR_USERNAME=${HR_USERNAME:-hr.admin}

while (($#)); do
  case "$1" in
    --base-url)
      if (($# < 2)); then
        printf 'Error: --base-url requires a URL.\n' >&2
        usage >&2
        exit 2
      fi
      BASE_URL=$2
      shift 2
      ;;
    --hr-username)
      if (($# < 2)); then
        printf 'Error: --hr-username requires a username.\n' >&2
        usage >&2
        exit 2
      fi
      HR_USERNAME=$2
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Error: unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

for dependency in curl jq; do
  if ! command -v "$dependency" >/dev/null 2>&1; then
    printf 'Error: required dependency not found: %s\n' "$dependency" >&2
    exit 127
  fi
done

if [[ -z ${HR_PASSWORD:-} ]]; then
  printf 'Error: set HR_PASSWORD before running this script.\n' >&2
  exit 2
fi
if [[ -z ${TEMPORARY_PASSWORD:-} ]]; then
  printf 'Error: set TEMPORARY_PASSWORD before running this script.\n' >&2
  exit 2
fi

BASE_URL=${BASE_URL%/}
HTTP_STATUS=
HTTP_HEADERS=
HTTP_BODY=
AUTH_COOKIE=

post_json() {
  local url=$1
  local payload=$2
  local response
  local curl_status=0
  local -a curl_args

  curl_args=(--silent --show-error --include --request POST
    --header 'Content-Type: application/json' --data "$payload")
  if [[ -n $AUTH_COOKIE ]]; then
    curl_args+=(--header "Cookie: access_token=$AUTH_COOKIE")
  fi
  if [[ ${CURL_INSECURE:-0} == 1 ]]; then
    curl_args+=(--insecure)
  fi

  response=$(curl "${curl_args[@]}" "$url" 2>&1) || curl_status=$?
  if [[ $response =~ ^HTTP/[^[:space:]]+[[:space:]]([0-9]{3}) ]]; then
    HTTP_STATUS=${BASH_REMATCH[1]}
    if [[ $response == *$'\r\n\r\n'* ]]; then
      HTTP_HEADERS=${response%%$'\r\n\r\n'*}
      HTTP_BODY=${response#*$'\r\n\r\n'}
    else
      HTTP_HEADERS=$response
      HTTP_BODY=
    fi
  else
    HTTP_STATUS=
    HTTP_HEADERS=
    HTTP_BODY=$response
  fi

  if ((curl_status != 0)); then
    return 1
  fi
  return 0
}

print_response() {
  printf '%s response (HTTP %s):\n%s\n' "$1" "${HTTP_STATUS:-unknown}" "$HTTP_BODY"
}

fail_response() {
  print_response "$1" >&2
  printf 'Error: %s failed; no further users will be created.\n' "$1" >&2
  exit 1
}

login_payload=$(jq -n --arg username "$HR_USERNAME" --arg password "$HR_PASSWORD" \
  '{username: $username, password: $password}') || exit 1
if ! post_json "$BASE_URL/auth/login" "$login_payload"; then
  fail_response 'HR login'
fi
if [[ $HTTP_STATUS != 200 ]]; then
  fail_response 'HR login'
fi
print_response 'HR login'

if [[ $HTTP_HEADERS =~ access_token=([^;[:space:]]+) ]]; then
  AUTH_COOKIE=${BASH_REMATCH[1]}
else
  printf 'Error: HR login succeeded but its response did not include an access_token cookie.\n' >&2
  exit 1
fi

hr_id=$(jq -er '.id | select(type == "number")' <<<"$HTTP_BODY") || {
  printf 'Error: HR login response did not include a numeric user id.\n' >&2
  exit 1
}

create_user() {
  local username=$1
  local role=$2
  local manager_id=$3
  local payload
  local user_id

  payload=$(jq -n \
    --arg username "$username" \
    --arg role "$role" \
    --argjson managerId "$manager_id" \
    --arg temporaryPassword "$TEMPORARY_PASSWORD" \
    '{username: $username, role: $role, managerId: $managerId, temporaryPassword: $temporaryPassword}') || {
      printf 'Error: could not build request for %s.\n' "$username" >&2
      return 1
    }

  if ! post_json "$BASE_URL/users" "$payload"; then
    fail_response "Creating $username"
  fi
  if [[ $HTTP_STATUS != 201 ]]; then
    fail_response "Creating $username"
  fi
  print_response "Creating $username"

  user_id=$(jq -er '.id | select(type == "number")' <<<"$HTTP_BODY") || {
    printf 'Error: successful response for %s did not include a numeric user id; stopping.\n' "$username" >&2
    return 1
  }
  printf 'Created %s (%s) with id %s.\n' "$username" "$role" "$user_id"
  REPLY=$user_id
}

create_user sunil MANAGER "$hr_id" || exit 1
sunil_id=$REPLY
create_user pd MANAGER "$sunil_id" || exit 1
pd_id=$REPLY
create_user anil EMPLOYEE "$pd_id" || exit 1
create_user jay EMPLOYEE "$pd_id" || exit 1
create_user pg EMPLOYEE "$pd_id" || exit 1
create_user rajesh EMPLOYEE "$pd_id" || exit 1
