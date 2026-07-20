// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
	integrations: [
		starlight({
			title: 'Agrotrack — Manual Técnico',
			sidebar: [
				{ label: 'Introducción', slug: 'introduccion' },
				{
					label: 'Arquitectura',
					items: [{ autogenerate: { directory: 'arquitectura' } }],
				},
				{
					label: 'Pruebas',
					items: [{ autogenerate: { directory: 'pruebas' } }],
				},
				{
					label: 'Historias de usuario',
					items: [{ autogenerate: { directory: 'historias-de-usuario' } }],
				},
			],
		}),
	],
});
