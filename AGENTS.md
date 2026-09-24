# Agent notes — TEC POC

## Canonical technical documentation

Prefer these files over inventing CCD / ExUI topology. They are the GOV.UK Tech Docs sources under `tech_docs/source/`:

| Topic | Path |
| --- | --- |
| Where TEC sits in HMCTS | [`tech_docs/source/hmcts-context.html.md.erb`](tech_docs/source/hmcts-context.html.md.erb) |
| Decentralised CCD architecture | [`tech_docs/source/ccd-architecture.html.md.erb`](tech_docs/source/ccd-architecture.html.md.erb) |
| State and event model (hand-edited diagrams) | [`tech_docs/source/state-event-model.html.md.erb`](tech_docs/source/state-event-model.html.md.erb) |
| Next step events matrix (worksheet) | [`tech_docs/source/next-step-events-matrix.html.md.erb`](tech_docs/source/next-step-events-matrix.html.md.erb) |
| CFTLib / database ownership | [`tech_docs/source/cftlib-shared-database.html.md.erb`](tech_docs/source/cftlib-shared-database.html.md.erb) |
| Local `bin/` scripts inventory | [`tech_docs/source/local-scripts.html.md.erb`](tech_docs/source/local-scripts.html.md.erb) |
| Local demo catalogue seed (orchestrator) | [`tech_docs/source/demo-catalogue-seed.html.md.erb`](tech_docs/source/demo-catalogue-seed.html.md.erb) |
| ExUI Upload batch file nav (local proxy) | [`tech_docs/source/exui-navigation.html.md.erb`](tech_docs/source/exui-navigation.html.md.erb) |
| ExUI Upload batch file architect proposal | [`tech_docs/source/exui-manage-batches-plan.html.md.erb`](tech_docs/source/exui-manage-batches-plan.html.md.erb) |
| Example menuConfigs JSON | [`tech_docs/source/exui-header-config.example.json`](tech_docs/source/exui-header-config.example.json) |

Tech docs overview: [`tech_docs/source/index.html.md.erb`](tech_docs/source/index.html.md.erb).
Design docs (blockframes / nesting demo): [`design_docs/source/index.html.md.erb`](design_docs/source/index.html.md.erb).

## Local preview

`./gradlew bootWithCCD` starts both Middleman sites (soft-fails if Ruby/Bundler are missing):

- Design docs: `bin/start-design-docs.sh` → **http://localhost:4567**
- Tech docs: `bin/start-tech-docs.sh` → **http://localhost:4568**

Or run either start script alone.
