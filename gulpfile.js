'use strict'

/**
 * Dependencies
 */

const $ = require('gulp-load-plugins')()
const bsync = require('browser-sync').create()
const gulp = require('gulp')
const {obj: passthrough} = require('through2')
const webpack = require('webpack')

const {devPort: PORT} = require('./package')
const webpackConfig = require('./webpack.config')

const PROD = process.env.NODE_ENV === 'production'

/**
 * Globals
 */

const staticDir = 'public'
const iconDir = 'node_modules/feather-icons/dist/icons'
const iconFiles = 'node_modules/feather-icons/dist/icons/**/*.svg'
const iconOutDir = 'public/icons'
const styleOutDir = 'public/styles'
const styleFiles = 'src-scss/**/*.scss'
const styleEntryFile = 'src-scss/main.scss'

const autoprefixerSettings = {browsers: ['> 1%', 'IE >= 10', 'iOS 7']}

const cssCleanSettings = {
  keepSpecialComments: 0,
  aggressiveMerging: false,
  advanced: false,
  // Don't inline `@import: url()`
  processImport: false,
}

const Err = (key, msg) => new $.util.PluginError(key, msg, {showProperties: false})

/**
 * Static
 */

gulp.task('static:build', () => (
  gulp.src(iconFiles, {base: iconDir})
  .pipe(gulp.dest(iconOutDir))
))

/**
 * Styles
 */

gulp.task('styles:build', () => (
  gulp.src(styleEntryFile)
    .pipe($.sass())
    .pipe($.autoprefixer(autoprefixerSettings))
    .pipe(!PROD ? passthrough() : $.cleanCss(cssCleanSettings))
    .pipe(gulp.dest(styleOutDir))
))

gulp.task('styles:watch', () => {
  $.watch(styleFiles, gulp.series('styles:build'))
})

/**
 * Scripts
 */

gulp.task('scripts:build', done => {
  buildWithWebpack(webpackConfig, done)
})

function buildWithWebpack(config, done) {
  return webpack(config, (err, stats) => {
    if (err) {
      done(Err('webpack', err))
    }
    else {
      $.util.log('[webpack]', stats.toString(config.stats))
      done(stats.hasErrors() ? Err('webpack', 'plugin error') : null)
    }
  })
}

gulp.task('scripts:watch', () => {
  watchWithWebpack(webpackConfig)
})

function watchWithWebpack(config) {
  const compiler = webpack(config)

  const watcher = compiler.watch({}, (err, stats) => {
    $.util.log('[webpack]', stats.toString(config.stats))
    if (err) $.util.log('[webpack]', err.message)
    else bsync.reload()
  })

  return {compiler, watcher}
}

/**
 * Browsersync
 */

gulp.task('bsync', () => (
  bsync.init({
    proxy: {
      target: `localhost:${PORT}`,
      proxyReq: [
        proxyReq => {
          // Don't mess with server host detection
          proxyReq.setHeader('host', `localhost:${PORT + 1}`)
        },
      ],
    },
    port: PORT + 1,
    files: staticDir,
    open: false,
    online: false,
    ui: false,
    ghostMode: false,
    notify: false,
  })
))

/**
 * Default
 */

gulp.task('buildup', gulp.parallel('static:build', 'styles:build'))

gulp.task('build', gulp.parallel('buildup', 'scripts:build'))

gulp.task('watch', gulp.parallel('styles:watch', 'scripts:watch', 'bsync'))

gulp.task('default', gulp.series('buildup', 'watch'))
