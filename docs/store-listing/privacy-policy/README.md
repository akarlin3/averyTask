# Privacy Policy — Hosting Setup

This folder contains PrismTask's privacy policy, which needs a public URL for the Play Console store-listing form. Recommended approach: publish it via GitHub Pages so the URL is stable, free, and under the same repo as the source code.

## Recommended placement

This folder sits at `docs/store-listing/privacy-policy/` for review. **Before publishing, move the `index.md` out to `docs/privacy/index.md`** so the URL is short and human-memorable:

```
https://akarlin3.github.io/prismTask/privacy/
```

vs. the less-tidy current path:

```
https://akarlin3.github.io/prismTask/store-listing/privacy-policy/
```

The actual privacy-policy text (`index.md`) and the `_config.yml` do not need to change when moved — only the folder. Update `privacy-policy-url` in Play Console accordingly.

## GitHub Pages enablement (one-time)

1. Go to [repo Settings → Pages](https://github.com/akarlin3/prismTask/settings/pages).
2. **Source:** "Deploy from a branch."
3. **Branch:** `main`.
4. **Folder:** `/docs`.
5. Click **Save**. GitHub will enable Pages and the site becomes available at `https://akarlin3.github.io/prismTask/` within ~1 minute. The privacy policy will be at `/privacy/` once the folder is moved as above.

**Note:** the repo must be public for free GitHub Pages, OR the account must be on GitHub Pro / Team / Enterprise to serve Pages from a private repo. PrismTask's repo is public (verified via `gh repo view akarlin3/prismTask --json isPrivate`), so no paid tier is needed.

## Plugging into Play Console

Play Console → Store presence → Store listing → **Privacy policy**: paste the final URL, e.g. `https://akarlin3.github.io/prismTask/privacy/`. Save.

Play Console will validate the URL is reachable. If it is a 404 at the time of paste, GitHub Pages either hasn't finished building or the folder wasn't moved; wait a minute and retry.

## Optional: custom domain

If you want the policy under `privacy.prismtask.app`:

1. In GoDaddy (or wherever `prismtask.app` is registered), add a DNS CNAME from `privacy` to `akarlin3.github.io`.
2. In the repo, create a `CNAME` file at the root of whatever folder GitHub Pages publishes (i.e. `docs/CNAME`) containing the single line `privacy.prismtask.app`.
3. In repo Settings → Pages, set the custom domain to `privacy.prismtask.app` and enable "Enforce HTTPS" once the TLS cert provisions.

The custom-domain path is optional — the github.io URL is sufficient for Play Console.

## Jekyll config (`_config.yml`)

A minimal `_config.yml` lives in this folder. If the policy is moved to `docs/privacy/`, the `_config.yml` should either move with it (if you want a dedicated Jekyll site) or be replaced by a single `_config.yml` at `docs/` that covers the whole Pages site. The simplest approach for one page: leave the policy as raw Markdown and skip the Jekyll config entirely — GitHub Pages renders `.md` files with a default theme.

## Keeping the policy in sync with the Data Safety form

**Load-bearing invariant:** this privacy policy and `compliance/data-safety-form.md` must agree on what data is collected, shared, and retained. Phase 3 verification cross-checks them. When you change one, update the other in the same commit.
