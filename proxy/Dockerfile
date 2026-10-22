FROM nginx:1.27-alpine

RUN apk add --no-cache openssl \
    && mkdir -p /etc/nginx/tls \
    && openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
        -subj "/CN=localhost" \
        -keyout /etc/nginx/tls/key.pem \
        -out /etc/nginx/tls/cert.pem

COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 443
