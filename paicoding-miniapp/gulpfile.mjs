import { rm } from 'node:fs/promises'
import { resolve } from 'node:path'
import { dest, parallel, series, src, watch as gulpWatch } from 'gulp'
import { WeappTailwindcss } from 'weapp-tailwindcss/gulp'

const outDir = 'dist'
const tailwindEntry = resolve('app.wxss')

const tw = WeappTailwindcss({
  cssEntries: [tailwindEntry],
  rem2rpx: true,
  platform: 'weapp',
  cssChildCombinatorReplaceValue: ['view', 'text'],
})

export async function clean() {
  await rm(outDir, { recursive: true, force: true })
}

export function wxss() {
  return src(['app.wxss', 'styles/**/*.wxss', 'pages/**/*.wxss', 'skills/**/*.wxss'], {
    base: '.',
    allowEmpty: true,
  })
    .pipe(tw.transformWxss())
    .pipe(dest(outDir))
}

export function wxml() {
  return src(['pages/**/*.wxml', 'skills/**/*.wxml'], {
    base: '.',
    allowEmpty: true,
  })
    .pipe(tw.transformWxml())
    .pipe(dest(outDir))
}

export function js() {
  return src(['app.js', 'utils/**/*.js', 'pages/**/*.js', 'skills/**/*.js'], {
    base: '.',
    allowEmpty: true,
  })
    .pipe(tw.transformJs())
    .pipe(dest(outDir))
}

export function assets() {
  return src(
    [
      'app.json',
      'project.config.json',
      'project.private.config.json',
      'sitemap.json',
      'package.json',
      'package-lock.json',
      'pages/**/*.json',
      'skills/**/*.{json,md}',
      'static/**/*',
      'styles/**/*.md',
      'miniprogram_npm/**/*',
    ],
    {
      base: '.',
      allowEmpty: true,
      dot: true,
    },
  ).pipe(dest(outDir))
}

export const build = series(clean, parallel(wxss, wxml, js, assets))

export function watch() {
  gulpWatch(
    [
      'app.{js,json,wxss}',
      'project*.json',
      'sitemap.json',
      'utils/**/*.js',
      'pages/**/*',
      'skills/**/*',
      'static/**/*',
      'styles/**/*',
      'miniprogram_npm/**/*',
    ],
    { ignoreInitial: false },
    build,
  )
}

export default build
