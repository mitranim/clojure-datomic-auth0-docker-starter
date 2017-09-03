(ns app.forms
  (:require
    [app.dat :as dat]
    [app.util :as util :refer [non-empty-str? sanitize-input eprn]]
    [app.pages :as pages]
    ))

(defn err [status error] {:status status :error error})

(defn worst-err-status [errs] (reduce max (map :status errs)))

(defn validate-post [{author :post/author, title :post/title, body :post/body}]
  (util/trim-args
    (when-not author                 (err 401 "A post must have an author, log in to create"))
    (when-not (non-empty-str? title) (err 400 "Must provide post title"))
    (when-not (non-empty-str? body)  (err 400 "Must provide post body"))))

(defn create-post [req]
  (let [pending {:post/author (get-in req [:user :db/id])
                 :post/title  (sanitize-input (get-in req [:params :post/title]))
                 :post/body   (sanitize-input (get-in req [:params :post/body]))}]
    (if-let [errs (validate-post pending)]
      {:status (worst-err-status errs)
       :body (pages/post-form {:post pending, :msg errs})}
      (try
        (let [post (dat/tx-dict! pending)]
          {:status 302
           :headers {"Location" (str "/posts/view/" (:db/id post))}
           :flash {:success "Post created!"}})
        (catch Exception err
          (eprn err)
          (pages/post-form {:post pending
                               :msg {:error util/err-unexpected}}))))))
