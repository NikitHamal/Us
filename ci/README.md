# CI workflow files

These are the real GitHub Actions workflows for this project. They live here **only** because the
agent's GitHub token did not have the `workflows` permission and GitHub therefore refused to push
files under `.github/workflows/`.

## Activate them (one minute, one commit)

Either:

**A. From your machine**
```bash
mkdir -p .github/workflows
cp ci/workflows/release.yml ci/workflows/ci.yml .github/workflows/
git add .github/workflows && git commit -m "Add CI workflows" && git push
```

**B. From the GitHub web UI**
Open each file here, copy its contents, then *Add file → Create new file* at
`.github/workflows/release.yml` and `.github/workflows/ci.yml`, paste, commit.

Once they are in `.github/workflows/`, pushing to `main` builds and signs the release APK
automatically — see the README for how to download it.
