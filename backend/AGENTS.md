<!-- bmad:context -->
<!-- Verified 2026-10-23 against dffed32512c44efd3ec701a83698876786766826. Managed by bmad-project-context; edits inside this block are replaced on refresh. -->

## company-leave backend

Backend conventions for Company Leave story implementation. The backend uses Java 21, Spring Boot 4.1.1, Maven, and a layered modular monolith organized by feature. Story requirements are in `_bmad-output/planning-artifacts/epics.md` and `_bmad-output/implementation-artifacts/`; the binding architecture is `_bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md`.

## Policy

- Before implementing any user story, explicitly read this context, the story's acceptance criteria and detailed spec when present, and the Architecture Spine; resolve any open design decision before coding.
- Use Lombok for boilerplate in every Java model/POJO; select annotations that fit the type, and avoid `@Data` on JPA entities unless explicitly justified.

## Where things are

- Binding backend design and module rules: `_bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md`.
- Story acceptance criteria: `_bmad-output/planning-artifacts/epics.md`; detailed story specs: `_bmad-output/implementation-artifacts/`.
- Story-level curl examples: `backend/curl/`; follow existing guides when adding a story guide.

## Conventions that differ from defaults

- Keep the package-by-feature layers `controller → service → repository`; call other modules only through their owning `@Service`, synchronously, without an event bus. Keep `common` inert as defined by the Architecture Spine.
- Before coding each story, name its applicable architecture pattern and non-obvious coding practices in the story implementation notes; add reusable project-wide practices here before relying on them.
- When a story is complete, add `backend/curl/story-<epic>.<story>-<slug>.md` with runnable curl examples and expected outcomes for success and important validation/authorization cases. Run the examples against the local stack when available and report any that could not be run.

<!-- /bmad:context -->
