(defproject app "0.0.0"
  :dependencies [
    [org.clojure/clojure "1.8.0"]
    [com.datomic/datomic-pro "0.9.5561"]
    [ring "1.6.1"]
    [compojure "1.6.0"]
    [hiccup "1.0.5"]
    [ring/ring-defaults "0.3.1"]
    [auth0-ring "0.4.3"]
    [org.clojure/tools.namespace "0.2.11"]
    [alxlit/autoclave "0.2.0"]
    [com.atlassian.commonmark/commonmark                       "0.9.0"]
    [com.atlassian.commonmark/commonmark-ext-autolink          "0.9.0"]
    [com.atlassian.commonmark/commonmark-ext-heading-anchor    "0.9.0"]
    [com.atlassian.commonmark/commonmark-ext-gfm-strikethrough "0.9.0"]
    [com.atlassian.commonmark/commonmark-ext-gfm-tables        "0.9.0"]

    ; Secondary dependencies for faster rebuilds. Listing all plugins and plugin
    ; dependencies here prevents us from redownloading them in Docker when
    ; recompiling the app.
    [lein-ring "0.12.1"]
    [lein-dotenv "RELEASE"]
    [commons-codec "1.10"]
    [org.clojure/tools.nrepl "0.2.3"]
    [ring-server "0.5.0"]
  ]

  ; Overrides for slimmer builds
  :exclusions [
    ring
    commons-codec
  ]

  :target-path "target/%s"

  ; nil = use default
  :uberjar-name #=(eval (System/getenv "UBERJAR_NAME"))

  :plugins [
    [lein-ring "0.12.1"]
    [lein-dotenv "RELEASE"]
  ]

  :ring {:handler app.core/app
         :port 9312
         :auto-refresh? true
         :re-resolve true
         :nrepl {:start? true}
         :stacktrace-middleware app.util/error-display-middleware}
)
