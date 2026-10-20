# OpenRewrite recipes for the Boot 2.7 -> 3.5 migration

Companion to the `spring-boot4-migration` skill, which starts at Boot 3.5. These cover the part before it:
Boot 2.7 -> 3.5. Nothing here modifies `build.gradle.kts`: the plugin is injected with an init script, so there
is nothing to clean up afterwards.

**Status: the recipes are untested.** The dry run was blocked in the session that wrote them. Run the dry run
yourself and read the patch before applying anything (see below). In particular, check how much of the
Kotlin sources and `*.gradle.kts` files the Java-oriented recipes actually touch. The zally-server module is
100% Kotlin, and OpenRewrite's Kotlin coverage is partial.

## Usage

JDK 17 is required (Kotlin 1.8 does not know JDK 21). From `server/`:

```bash
# 1. preview, writes build/reports/rewrite/rewrite.patch and changes no source file
./gradlew --init-script openrewrite/init.gradle rewriteDryRun \
  -Drewrite.activeRecipe=org.zalando.zally.migration.Boot2.Step1_Junit5

# 2. apply
./gradlew --init-script openrewrite/init.gradle rewriteRun \
  -Drewrite.activeRecipe=org.zalando.zally.migration.Boot2.Step1_Junit5
```

Then review `git diff`, run `./gradlew build`, commit, and continue with the next recipe.

## Recipes (run in this order, one commit each)

| # | Recipe | What it does | Still manual afterwards |
|---|--------|--------------|-------------------------|
| 1 | `Step1_Junit5` | JUnit 4 -> 5 (SpringRunner, rules). Runs on Boot 2.7. | Verify `NullPointerExceptionTest` and `RestApiTestConfiguration`; drop `junit-vintage-engine`. |
| 2 | `Step2_Tooling` | Gradle wrapper -> 8.14.x, Kotlin plugins -> 2.2.x. | Dokka 2.x (`dokkaJavadoc` task is gone), ktlint/Spotless, kapt, pinned versions in the root `build.gradle.kts`. |
| 3 | `Step3_UpgradeTo35` | Boot 3.5 composite: javax -> jakarta, Security 6.5, Hibernate 6, property renames, Flyway 10 + Postgres module. | See the "Manual work" list below. |
| 3b | `Step3b_RemoveLegacyLibraries` | Removes `stups-spring-oauth2-server`, `twintip-spring-web`, `problem-spring-web`, `usertype.core`. Opt-in: compilation fails on purpose. | Write the replacements. |

Pinned tool versions (`7.39.0` plugin, `rewrite-spring 6.31.0`) match the `spring-boot4-migration` skill.
Newer releases exist (plugin 7.41.0, rewrite-spring 6.37.1); bump them together with the skill.

## Manual work OpenRewrite will not do for Zally

- `OAuthConfiguration` / `NoSecurityConfiguration`: rewrite to `SecurityFilterChain` with the built-in resource
  server and a tokeninfo `OpaqueTokenIntrospector`. The legacy `#oauth2.hasScope('uid')` expression has no recipe.
- `ApiReview.kt`: `@Type(PersistentOffsetDateTime)` -> native `OffsetDateTime` mapping with UTC storage. The
  `created` column is `TIMESTAMP WITHOUT TIME ZONE` and `ddl-auto` is `validate`, so check the mapping.
- `ApiReviewRepository` HQL: `isSuccessfulProcessed = 'True'`, and the unqualified column names `day` and
  `user_agent` are rejected or behave differently under Hibernate 6.
- `WebMvcConfiguration`: `favorPathExtension(true)` (`.yaml` suffix negotiation) and trailing-slash URLs
  (`/health/`, `/api-violations/`). Spring 6 stops matching trailing slashes by default.
- Replace `twintip` (2 classes, `javax.servlet`) with a small controller serving `/api`.
- `application*.yml`: `spring.profiles: local`, `endpoints.*`, `security.basic.enabled`, `non_contextual_creation`.
- Pinned versions in `build.gradle.kts` (Jackson 2.14.1, snakeyaml 1.33, mockito, logback, junit 5.9.1, flyway
  9.11.0): explicit versions beat Boot's dependency management, so these silently hold the stack back.
