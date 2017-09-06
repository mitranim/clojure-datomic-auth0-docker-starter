## Overview

This is a web server template that uses:

  * [Clojure](https://clojure.org) as application language
  * [Datomic](http://www.datomic.com) as database
  * [Auth0](https://auth0.com) for authentication
  * [Node](https://nodejs.org) for frontend build
  * [Docker](https://www.docker.com) for deployment

## TOC

* [Setup](#setup)
  * [Leiningen + Maven](#leiningen--maven)
  * [Datomic](#datomic)
  * [Auth0](#auth0)
  * [Env Secrets](#env-secrets)
  * [Node](#node)
* [Development](#development)
  * [Run Datomic](#run-datomic)
  * [Run Frontend Build](#run-frontend-build)
  * [Run App](#run-app)
  * [REPL Into App](#repl-into-app)
* [Deployment](#deployment)
  * [Local Check](#local-check)
  * [VPS Setup](#vps-setup)
  * [VPS Deployment](#vps-deployment)
  * [Process Management](#process-management)

## Setup

First off, clone and rename the repository:

```sh
git clone https://github.com/Mitranim/clojure-datomic-auth0-docker-starter.git my-app-name
```

Some of the setup steps are only relevant in development mode, and happen
automatically in Docker. See [Deployment](#deployment).

### Leiningen + Maven

The app runs on Clojure and requires Leiningen, the project/dependency manager
for Clojure. Get it: https://leiningen.org

We also need Maven for the Datomic peer library. On MacOS, install it with
Homebrew: `brew install maven`. On other Unix systems, use your standard package
manager. Otherwise, check the official instructions:
https://maven.apache.org/install.html.

### Datomic

Datomic is our database. Read about it on http://www.datomic.com. Despite its
advanced design, Datomic has a somewhat archaic setup process; don't let this
deter you.

1. Get Datomic license

Register on http://www.datomic.com.

We're using Datomic Pro Starter. The website variously shortens it to "Datomic
Starter", and it also has a "Datomic Free". Navigate this maze with confidence:
Datomic Pro Starter _is_ free, and is exactly what we want.

2. Download

Go to https://my.datomic.com/downloads/pro and download the _exact_ same version
as our Datomic dependency in `project.clj`:
https://my.datomic.com/downloads/pro/0.9.5561. Unzip it somewhere. The download
includes:

  * transactor server
  * peer library for use in our app process

Unzip and navigate to the resulting folder:

```sh
cd <unzipped folder>
```

3. Make peer library available

The peer library is the dependency `com.datomic/datomic-pro` in `project.clj`.
It's what our app uses to connect to Datomic and run queries. It's not in a
public repository, and comes with the Datomic download. Assuming you have Maven
installed, run this command:

```sh
bin/maven-install
```

This makes it locally available to Leiningen.

Note: you can connect to Datomic as a _client_ or a _peer_. A client runs every
request over the network, while a peer caches data locally. Being a peer costs
memory, but gives you better read performance and negates network latency
between app and database. Peer are also fantastic for horizontal scaling.
Unfortunately the official Datomic tutorial focuses on a client, while being a
peer is what you really want, and that's what we're doing.

4. Setup config

We'll be using the self-sufficient "dev" configuration. Copy the template:

```sh
cp config/samples/dev-transactor-template.properties config/transactor.properties
```

5. Add license keys

You need to add the Datomic license key to two property files:

  * `config/transactor.properties` in the unzipped Datomic folder
  * `config/transactor.properties` in the app repository

Open the files and fill the `license-key=` field. To get the key, go to
https://my.datomic.com/account and click "Send License Key" to receive it in an
email. This is _not_ the short download key immediately visible on the page.

6. Run transactor

```sh
bin/transactor config/transactor.properties
```

This is the core database process. Keep it running forever.

In the "dev" configuration, this process is self-sufficient and manages storage
by itself, using local files. In other configurations, you also need to manage
the storage database the transactor connects to.

7. Create database

The app will do this automatically. See `src/app/dat.clj` → `setup-db`.

8. Transact schema

The app will do this automatically. See `src/app/dat.clj` → `setup-db`.

### Auth0

This app uses Auth0, a cloud authentication service: https://auth0.com. It has a
free plan suitable for small apps.

1. Register on Auth0 at https://auth0.com.

2. Create a tenant. In Auth0 terms: "tenant" = "account" ≈ app ≈ brand. You need
   one "tenant" per application. The "create tenant" button is in the dropdown
   under your profile. Make sure to pick the region closest to your users.

3. Create a "client" for that tenant. It represents your app. When prompted for
   app type, select regular "Web App". When asked about technology, ignore it,
   scroll up, and click "Settings".

4. You should be seeing things like "Domain" and "Client ID". We'll copy these
   later into the [env secrets](#env-secrets).

5. If "Client Type" is set to "Regular Web Application", change "Token Endpoint
   Authentication Method" to "Post". Otherwise authentication will silently
   fail.

6. Allow application URLs

In "Allowed Callback URLs", add something like:

```
http://<host:port>/auth/callback,
http://<host:port+1>/auth/callback,
https://<host-prod>/auth/callback`
```

Where:
  * `<host:port>` is something like `localhost:9312` (see `project.clj` → `:ring` → `:port`)
  * `<host:port+1>` uses an incremented port for Browsersync
  * `<host-prod>` is your official domain.

In "Allowed Logout URLs", add something like:

```
http://<host:port>/auth/logout,
http://<host:port+1>/auth/logout,
https://<host-prod>/auth/logout
```

Replacing the hosts as before.

7. Change the JWT signature algorithm to HS256. Scroll to bottom → "Show
   Advanced Settings" → "OAuth" → "JsonWebToken Signature Algorithm" → select
   HS256. It requires less setup and is good enough for our purposes. If you
   skip this step, authentication will silently fail.

8. (Optional) Add more fields to JWT

On authentication, our app receives a few access keys, one of them a JWT ([JSON
Web Token](https://jwt.io)) with a subset of the user profile. We can request
the full profile from Auth0, or add more fields to the JWT.

By default, this app uses the JWT since it's less brittle than making network
requests. The downside is the increased cookie size.

In the Auth0 control panel, click "Rules" in the sidebar, then "Create Rule"
→ "Empty Rule". Insert and save the following:

```js
function (user, context, callback) {
  const prefix = 'http://app/'
  if (user.name) context.idToken[prefix + 'name'] = user.name
  if (user.nickname) context.idToken[prefix + 'nickname'] = user.nickname
  if (user.picture) context.idToken[prefix + 'picture'] = user.picture
  callback(null, user, context)
}
```

This code will run on Auth0 servers on every authentication, extending the JWT.
If you skip this step, modify the function `dat/jwt-user-to-user-entity`,
removing the weird prefixed fields.

### Env Secrets

Some settings must live outside of source control because they're either secret
or environment-specific. Furthermore, you want _multiple_ environments with
different secrets and settings.

`cd` to the app folder and rename the example files:

```sh
mv .env.example       .env
mv .local.env.example .local.env
mv .prod.env.example  .prod.env
```

Purpose:

  * `.env` is for local development; it's implicitly active
  * `.local.env` is for local `docker-compose` builds
  * `.prod.env` is for remote `docker-compose` builds

Inside each file, replace `<placeholders>` with your own settings. Notes:

  * `<my-db-name>` is arbitrary; name it after your app
  * get Auth0 settings on https://manage.auth0.com → "Clients" → your app
  * get Datomic credentials on https://my.datomic.com/account
  * `SERVER_PORT` is the public port facing the Internet

The basic `.env` file is slightly magical:

  * [`lein-dotenv`](https://github.com/tpope/lein-dotenv) exports it to the JVM
    environment
  * `docker-compose` exports it to its own environment

Later, when building with Docker, run `./use-env` to activate a different
environment:

```sh
./use-env .local.env
```

```sh
./use-env .prod.env
```

To deactivate the environment, use `exit`, `Ctrl+D`, or simply open a new tab.

This setup has nice properties:

  * one format that works with Docker, Clojure, Node, etc
  * entirely shell-based, no file editing
  * open set of environments
  * easy to extend and override in CI

### Node

The frontend build system requires Node.js. See the installation instructions on
https://nodejs.org.

## Development

In development, you run 3-4 separate processes:

* Datomic
* Frontend build
* App
* REPL into app

### Run Datomic

Start the transactor like in the [Datomic Setup](#datomic) section:

```sh
cd <datomic-path>
bin/transactor config/transactor.properties
```

### Run Frontend Build

Install dependencies:

```sh
npm i
```

Check `package.json` → `"scripts"`. It stores arbitrary shell scripts that can
be run with `npm run <name>`. Useful for application-specific commands that can
be hard to remember.

Run in development mode:

```sh
npm run -s front-start
```

This will build frontend assets and keep watching and rebuilding them as you
edit. It will also proxy to the Clojure server, using
[Browsersync](https://www.browsersync.io) to re-inject stylesheets without
reloading the page. Handy for GUI development. It will print a proxy URL to the
terminal; use it instead of the standard URL.

The build system runs on [Gulp
4](https://github.com/gulpjs/gulp/blob/4.0/docs/API.md) and
[Webpack](https://webpack.js.org). It uses [SCSS](http://sass-lang.com) for
styles and [Babel](http://babeljs.io) for scripts. It also installs `eslint_d`
for fast in-editor linting. Check if your editor has an `eslint_d` plugin.

### Run App

Run in development mode:

```sh
lein ring server-headless
```

It will take a few seconds to start. During this time, it boots the Clojure
runtime, compiles your Clojure code, and executes namespaces.

The app uses [Ring](https://github.com/ring-clojure/ring) as the server
framework, and [Hiccup](https://github.com/weavejester/hiccup) for templating.

#### Bonus Dependencies

These are included because they solve very common problems: user input and
output. Feel free to replace or remove them.

1. Sanitisation

You need to sanitise user input/output to avoid XSS vulnerabilities. This app
uses [`autoclave`](https://github.com/alxlit/autoclave), a wrapper around
[OWASP](https://www.owasp.org/index.php/OWASP_Java_HTML_Sanitizer_Project). See
sanitisation functions in `util.clj`. We use reasonable, fairly permissive
defaults.

2. Markdown

Markdown for user input is becoming commonplace.

I was unable to find a Markdown library for Clojure that wasn't buggy or wasn't
missing common-sense features. As a result, this template uses Atlassian's [Java
implementation](https://github.com/atlassian/commonmark-java) of Commonmark with
some common-sense extensions. Feel free to customise or replace it.

#### Auto-reload

The [`lein-ring`](https://github.com/weavejester/lein-ring) plugin will watch
your code and automatically recompile and reload it _in the running
application_. Any open pages will automatically refresh.

To save you surprises, here's the apparent limitations of this auto-reload and
auto-refresh, as currently implemented:

  * Code is reloaded in response to HTTP requests; when editing abstract
    functionality without any pages open, reload manually from the REPL;
    see below

  * The refresh script is only injected if: the request is `:get`; the response
    has status 200; the response has the HTML content type; the content has the
    `<html><head>` opening tags

  * As a corollary, pages that are exempt from auto-refresh include: non-HTML
    endpoints; error pages; POST submissions

  * When quickly editing multiple namespaces, or if a newly reloaded namespace
    produces a compile or runtime error, auto-reload might fail; run a manual
    refresh command from the REPL

This app template supplies a middleware to `lein-ring` that lifts the status
restriction, auto-refreshing error pages when you Cmd+Tab into them.

### REPL Into App

_REPL_ stands for Read Eval Print Loop. Any interactive command prompt is a
REPL. Clojure has one, too.

After starting the app, connect via nREPL (network REPL):

```sh
lein repl :connect
```

Once connected, you find yourself in the `user` namespace. Move to the main app
namespace:

```clj
(ns app.core)
```

Since you're connected to the running app, code changes and auto-reloads affect
the REPL, and changes made in the REPL affect the server.

When auto-reload gets stuck, reload manually from the REPL:

```clj
(clojure.tools.namespace.repl/refresh)
; or
(R)
```

## Deployment

This app comes with a Docker configuration for hassless deployment. Docker is a
tool for lightweight virtualisation and automation of builds and deployments.
Get it here: https://www.docker.com. Make sure you have the following
executables: `docker`, `docker-compose`, `docker-machine`.

This guide describes deploying to a VPS using `docker-machine`, but you can use
any other method that works with Docker.

### Local Check

To make sure the image works, build and run it locally. [Activate](#env-secrets)
the local Compose env before running commands.

```sh
./use-env .local.env
docker-compose up --build
```

This will take a while. Eventually, you should see the familiar startup
messages. Datomic will launch first, followed by the application server. Since
there's no easy way to wait before Datomic is fully ready, sometimes the server
launches too early, fails to connect, croaks, and restarts. Eventually it
connects.

Test it by opening the webpage on the usual port. When done, stop it:

```sh
docker-compose down
```

### VPS Setup

Get a VPS (Virtual Private Server) with SSH access. You can use a cloud provider
such as Linode or DigitalOcean. Check the provider's tutorials on basic setup.

When choosing the operating system, pick the safest default, which basically
means Ubuntu LTS.

Make sure to run all commands in this tutorial under the same user. If you
decide to use a non-root user account, enable passwordless sudo for that
account. Without it, `docker-machine` might fail.

Also, give your user account passwordless SSH access:

```sh
ssh-copy-id <user>@<addr>
```

Substituting:

  * `<user>` → preferred VPS user
  * `<addr>` → VPS address

**Don't** install Docker on the VPS. If you do, wipe the VPS and start from
scratch. `docker-machine` installs Docker automatically; an existing version
creates a conflict.

### VPS Deployment

#### `docker-machine`

Before we can build and run, we need `docker-machine` to take control of the
VPS.

Make sure your version of `docker-machine` is at least `0.12.2`. At the time of
writing, the default MacOS installation comes with `0.12.0` which is
incompatible with Ubuntu 16.04 LTS. Follow the instructions on
https://docs.docker.com/machine/install-machine/ to get an up-to-date version.

Check out `docker-machine help` and its documentation.

Run the following:

```sh
docker-machine create -d generic \
  --generic-ip-address=<addr> \
  --generic-ssh-user=<user> \
  --generic-ssh-key=$HOME/.ssh/id_rsa \
  --engine-storage-driver=overlay \
  <vm-name>
```

Substituting:

  * `<addr>` → VPS address
  * `<user>` → preferred VPS user
  * `<vm-name>` → cosmetic name

`--engine-storage-driver=overlay` is a workaround for `docker-machine`
defaulting to the `aufs` driver, which is missing by default from Ubuntu 16.04.
This might not be relevant for other systems or versions.

The command should say something along the lines of "Docker is up and running".
Activate that machine in the current shell:

```sh
eval $(docker-machine env <vm-name>)
```

Verify it:

```sh
docker-machine active
```

It should report the `<vm-name>`.

This only affects the current shell. When using multiple terminal tabs, activate
the machine in each tab that operates on it.

#### `docker-compose`

Now we can deploy! When a docker-machine is activated, normal `docker` and
`docker-compose` commands run _remotely_ rather than locally, but work exactly
the same. Build, debug and deploy like in [Local Check](#local-check):

Make sure to [activate](#env-secrets) the production Compose env before running
commands.

```sh
./use-env .prod.env
docker-compose up --build
```

By default, this will attach your shell to the process, which is handy for
debugging. Detach with Ctrl-C. If this kills the containers, restart in detached
mode:

```sh
docker-compose up -d
```

Make sure the containers are running:

```sh
docker-compose ls
```

### Process Management

Processes that run forever, like databases and servers, must use a process
manager that will automatically start and restart them. Our `docker-compose.yml`
instructs Docker to restart the containers in case of failure, and
`docker-machine` configures the Docker daemon to start automatically.

Double check this by rebooting the VPS. The server should eventually come back
online.

## Misc

For any questions, open an
[issue](https://github.com/Mitranim/clojure-datomic-auth0-docker-starter/issues)
or ping me at me@mitranim.com.
