'use strict'

const pt = require('path')
const webpack = require('webpack')

const PROD = process.env.NODE_ENV === 'production'

const SRC_DIR = pt.resolve('src-js')
const PUBLIC_DIR = pt.resolve('public')

module.exports = {
  entry: pt.join(SRC_DIR, 'main'),

  output: {
    path: PUBLIC_DIR,
    filename: 'main.js',
  },

  module: {
    rules: [
      {
        test: /\.js$/,
        include: SRC_DIR,
        use: {loader: 'babel-loader'},
      },
    ],
  },

  plugins: [
    ...(!PROD ? [] : [
      new webpack.optimize.UglifyJsPlugin({
        mangle: true,
        toplevel: true,
        compress: {warnings: false},
      }),
    ]),
  ],

  stats: {
    assets: false,
    colors: true,
    hash: false,
    modules: false,
    timings: true,
    version: false,
  },
}
