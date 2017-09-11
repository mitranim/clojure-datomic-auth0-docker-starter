(ns app.core
  (:use [clojure.repl])
  (:require
    [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
    [auth0-ring.handlers :as auth0]
    [auth0-ring.middleware :refer [wrap-token-verification]]
    [compojure.core :refer [routes GET POST ANY]]
    [compojure.route :as route]
    [expiring-map.core :as em]

    ; REPL
    [clojure.tools.namespace.repl :refer [refresh] :rename {refresh R}]
    [clojure.pprint :refer [pprint] :rename {pprint ppr}]
    [datomic.api :as d]
    [app.dat :as dat :refer [db auth0-config]]
    [app.util :as util :refer [eprn eprintln to-html-res to-html-coded]]
    [app.pages :as pages]
    [app.i18n :as i18n]
    ))

(dat/setup-db)

(def handle-login (auth0/wrap-login-handler (comp to-html-res pages/login)))
(def handle-logout (auth0/create-logout-handler auth0-config))
(def handle-login-callback
  (auth0/create-callback-handler
    auth0-config
    {:on-authenticated dat/on-authenticated-write-user-to-db}))
(def handle-logout-callback (auth0/create-logout-callback-handler auth0-config))

(def app-routes (routes
  (GET  "/"                    []   (to-html-res (pages/index)))
  (GET  "/posts/all"           []   (to-html-res (pages/posts-all)))
  (GET  "/posts/view/:id"      [id] (to-html-res (pages/post-view (Long/parseLong id))))
  (GET  "/posts/create"        []   (to-html-res (pages/post-create-form)))
  (GET  "/profile"             []   (to-html-res (pages/profile)))

  (POST "/posts/login-create"  []   (pages/post-login-create-submit))
  (POST "/posts/create"        []   (pages/post-create-submit))

  (GET  "/login"         [] handle-login)
  (GET  "/logout"        [] handle-logout)
  (GET  "/auth/callback" [] handle-login-callback)
  (GET  "/auth/logout"   [] handle-logout-callback)

  (ANY  "*" [] (to-html-coded 404 (pages/page-404)))

  (route/not-found "Not Found")))

(defonce session-store (util/expiring-session-store 72 {:time-unit :hours}))

(def app-defaults
  (-> site-defaults
      (assoc-in [:security :anti-forgery]
                {:error-handler #(to-html-coded 403 (pages/page-403-forgery %))})
      (assoc-in [:static :files] "public")
      (assoc-in [:session :store] session-store)))

(def app (-> app-routes
  ; Must run last
  util/wrap-with-dynamic-req
  i18n/wrap-use-param-lang
  i18n/wrap-use-session-lang
  i18n/wrap-detect-langs
  dat/wrap-rewrite-user
  util/wrap-restore-state
  util/wrap-keywordize-params
  (wrap-token-verification auth0-config)
  (wrap-defaults app-defaults)
  (util/wrap-handle-500 pages/page-500)
  ))
