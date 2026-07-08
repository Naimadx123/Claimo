// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import catppuccin  from '@catppuccin/starlight';

// https://astro.build/config
export default defineConfig({
	integrations: [
		starlight({
			title: 'Claimo',
			description:
				'Voucher / code redemption plugin for PaperMC 1.21.1–26.2. Players redeem codes, ' +
				'each running server commands and gated behind pluggable requirements.',
			logo: {
				src: './src/assets/logo.svg',
				alt: 'Claimo',
			},
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/Naimadx123/Claimo',
				},
				{
					icon: 'seti:yml',
					label: 'Modrinth',
					href: 'https://modrinth.com/plugin/claimo',
				},
			],
			editLink: {
				baseUrl: 'https://github.com/Naimadx123/Claimo/edit/dev/docs/',
			},
			lastUpdated: true,
			tableOfContents: { minHeadingLevel: 2, maxHeadingLevel: 3 },
			sidebar: [
				{
					label: 'Start here',
					items: [
						{ label: 'Introduction', slug: 'start/introduction' },
						{ label: 'Installation', slug: 'start/installation' },
						{ label: 'Quick start', slug: 'start/quick-start' },
					],
				},
				{
					label: 'Using Claimo',
					items: [
						{ label: 'Commands & permissions', slug: 'guides/commands' },
						{ label: 'The in-game creator', slug: 'guides/creator', badge: { text: '1.21.7+', variant: 'tip' } },
						{ label: 'Voucher files', slug: 'guides/vouchers' },
						{ label: 'Requirements', slug: 'guides/requirements' },
					],
				},
				{
					label: 'Configuration',
					items: [
						{ label: 'Overview', slug: 'configuration/overview' },
						{ label: 'config.yml', slug: 'configuration/config' },
						{ label: 'messages.yml', slug: 'configuration/messages' },
						{ label: 'gui.yml', slug: 'configuration/gui' },
						{ label: 'Storage backends', slug: 'configuration/storage' },
					],
				},
				{
					label: 'Integrations',
					items: [
						{ label: 'PlaceholderAPI', slug: 'integrations/placeholderapi' },
					],
				},
				{
					label: 'Developer API',
					badge: { text: 'Addons', variant: 'note' },
					items: [
						{ label: 'Overview', slug: 'api/overview' },
						{ label: 'Custom requirements', slug: 'api/requirements' },
						{ label: 'Events', slug: 'api/events' },
						{ label: 'API reference', slug: 'api/reference' },
					],
				},
			],
			plugins: [
				catppuccin({
					dark: { flavor: "mocha", accent: "yellow" },
					light: { flavor: "latte", accent: "yellow" },
				})
			],
		}),
	],
});
