# Claimo documentation

The Claimo documentation site, built with [Starlight](https://starlight.astro.build)
(Astro).

## Local development

```bash
cd docs
npm install
npm run dev      # dev server at http://localhost:4321
npm run build    # production build to ./dist/
npm run preview  # preview the production build
```

## Structure

Pages live in `src/content/docs/` as `.mdx` files; each file's path is its route. The
sidebar and site metadata are configured in `astro.config.mjs`.

```
src/content/docs/
├── index.mdx              # landing page
├── start/                 # introduction, installation, quick start
├── guides/                # commands, in-game creator, vouchers, requirements
├── configuration/         # config.yml, messages.yml, gui.yml, storage
├── integrations/          # PlaceholderAPI
└── api/                   # developer API — requirements, events, reference
```

> **Note:** before deploying, set the `site` option in `astro.config.mjs` to the public
> URL so the sitemap and canonical links are generated.
