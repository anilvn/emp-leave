- source_spec: `_bmad-output/implementation-artifacts/spec-leave-tracker-app-modules.md`
  summary: Preserve keyboard focus in the workspace after login.
  evidence: This behavior predates the app-module refactor and can leave keyboard users focused on a hidden submit control.
- source_spec: `_bmad-output/implementation-artifacts/spec-leave-tracker-app-modules.md`
  summary: Keep the reversed date-range validation message from being replaced by the weekday-range message.
  evidence: The validation order predates this refactor; an invalid reversed range currently receives a less useful error message.
- source_spec: `_bmad-output/implementation-artifacts/spec-leave-tracker-app-modules.md`
  summary: Give HR-created employees usable initial leave balances or clearly explain the zero-balance state.
  evidence: Account creation initializes all balances to zero, preventing request submission until balances are separately adjusted; this behavior predates the modularization.
