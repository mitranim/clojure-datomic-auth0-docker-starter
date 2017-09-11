(ns app.i18n
  (:require
    [clojure.string :as string]
    [app.util :as util :refer [*req* sort-by-items]]
  ))

(def SITE_LANGS [:en])

(def DEFAULT_LANG (first SITE_LANGS))

(def known-lang? (comp boolean (set SITE_LANGS)))

; https://github.com/hjnilsson/country-flags/tree/master/svg
(def flag-svg {
  :en "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 60 30\" width=\"1200\" height=\"600\"><clipPath id=\"a\"><path d=\"M30 15h30v15zv15H0zH0V0zV0h30z\"/></clipPath><path d=\"M0 0v30h60V0z\" fill=\"#00247d\"/><path d=\"M0 0l60 30m0-30L0 30\" stroke=\"#fff\" stroke-width=\"6\"/><path d=\"M0 0l60 30m0-30L0 30\" clip-path=\"url(#a)\" stroke=\"#cf142b\" stroke-width=\"4\"/><path d=\"M30 0v30M0 15h60\" stroke=\"#fff\" stroke-width=\"10\"/><path d=\"M30 0v30M0 15h60\" stroke=\"#cf142b\" stroke-width=\"6\"/></svg>"
})

(def phrase-registry {
  :SITE_NAME
  {:en "AppStarter"}

  :posts
  {:en "Posts"}

  :my-posts
  {:en "My Posts"}

  :required-author
  {:en "A post must have an author, log in to create"}

  :required-post-title
  {:en "Must provide post title"}

  :required-post-body
  {:en "Must provide post body"}

  :create-post
  {:en "Create Post"}

  :save-post
  {:en "Save Post"}

  :login-before-writing
  {:en "Login Before Writing"}

  :login-to-submit
  {:en "Login to Submit"}

  :post-created
  {:en "Post created!"}

  :no-posts-yet
  {:en "There are no posts yet"}

  :login
  {:en "Login"}

  :logout
  {:en "Logout"}

  :err-404
  {:en "404 Not Found"}

  :err-500
  {:en "500 Internal Server Error"}

  :err-401
  {:en "401 Unauthenticated"}

  :err-403-forgery
  {:en "403 Invalid Anti-Forgery Token"}

  :retry
  {:en "Retry"}

  :err-unexpected
  {:en "Unexpected error, please contact site administration"}

  :oops
  {:en "Oops, something went wrong. Sorry!"}

  :or
  {:en "or"}

  :home
  {:en "Home"}

  :welcome
  {:en "Welcome"}

  :by
  {:en "by"}

  :anon
  {:en "anonymous"}

  :at
  {:en "at"}

  :read-full
  {:en "read full"}

  :title
  {:en "Title"}

  :body
  {:en "Content"}

  :my-post-title
  {:en "My post title"}

  :my-post-body
  {:en "My post content"}

  :profile
  {:en "Profile"}
})

(defn xln [key & args]
  (let [phrases (get phrase-registry key)
        phrase (when phrases
                 (or (get phrases (:lang *req*))
                     (get phrases DEFAULT_LANG)
                     (val (first phrases))
                     key))]
    (if (fn? phrase) (apply phrase args) phrase)))

(defn parse-q-priority [q-str]
  (or (and (string? q-str)
           (Double/parseDouble (second (re-matches #"q=(\d\.\d|\.\d|\d)" q-str))))
      1))

(defn parse-lang-code [code]
  (-> code
      (string/split #";")
      (update-in [1] parse-q-priority)))

(defn only-prefix [string] (second (re-matches #"(\w+).*" string)))

(defn acceptable-langs [accept-lang-str]
  (->> (string/split accept-lang-str #",")
       (map parse-lang-code)
       (sort-by second)
       (map first)
       (filter string?)
       (map only-prefix)
       (filter string?)
       (map string/lower-case)
       (map keyword)
       reverse
       distinct))

(defn req-langs [request]
  (let [accept-lang (get-in request [:headers "accept-language"])]
    (when (string? accept-lang) (acceptable-langs accept-lang))))

(defn sorted-langs [request]
  (sort-by-items SITE_LANGS (req-langs request)))

(defn detect-langs [request]
  (let [langs (sorted-langs request)]
    (merge request {:langs langs :lang (first langs)})))

(defn wrap-detect-langs [handler] (comp handler detect-langs))

(defn use-session-lang [request]
  (let [session-lang (get-in request [:session :lang])]
    (if (known-lang? session-lang)
      (assoc request :lang session-lang)
      request)))

(defn wrap-use-session-lang [handler] (comp handler use-session-lang))

; Usage:
;   [:a {:href (str (:uri *req*) "?lang=LANG")}]
; The new language is used for the next request and stored in the session
(defn wrap-use-param-lang [handler]
  (fn use-param-lang [request]
    (let [req-lang (keyword (get-in request [:query-params "lang"]))]
      (if (and (= (:request-method request) :get) (known-lang? req-lang))
        (-> request
            (assoc :lang req-lang)
            handler
            (assoc-in [:session :lang] req-lang))
        (handler request)))))
