# Rename this folder to `.github`

GitHub refused the agent's push of `.github/workflows/**` because the bot token has no
`workflows` permission. Everything is ready — you just need to rename one folder.

```bash
git mv dot-github .github
git commit -m "Activate GitHub Actions workflows"
git push
```

Or on the GitHub web UI: open each file, click the pencil, and change the path's leading
`dot-github/` to `.github/`.

That's it. The next push builds and signs the APK.

## What you get

- **`release.yml`** — builds the signed release APK. Triggers on a push to **any branch**
  (when `app/**`, gradle files, `scripts/**` or the workflow itself changes), on any `v*` tag,
  and via **Run workflow** on any branch.
- **`ci.yml`** — unit tests + lint on any branch push and every pull request.

Grab the APK from **Actions → Release APK → your run → Artifacts → `us-release-apk`**.
