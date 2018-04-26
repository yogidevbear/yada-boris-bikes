(ns yada-boris-bikes.main
  (:require [yada.yada :as yada]
            [bidi.bidi :as bidi]
            [buddy.sign.jwt :as jwt]
            [schema.core :as s]
            [hiccup.core :refer [html]]
            ;[clj-http.client :as client]
            [clojure.data.json :as json]))

(def leyton-orient-fc {:lat 51.5601M :lon -0.012551M})

(defn ensure-positive
  "Takes a number.
   If negative, converts it to positive equivalent and returns.
   Otherwise, returns the original number."
   [x]
   (if (< x 0)
     (- x)
     x))

(defn get-hypotenuse-squared
  "Returns the square of the hypotenuse of a right angle triangle
   formed from two different latitudes and longitudes."
  [lat1 lat2 lon1 lon2]
  (let [lat-diff (ensure-positive (- lat1 lat2))
        lon-diff (ensure-positive (- lon1 lon2))]
    (+ (* lat-diff lat-diff)
       (* lon-diff lon-diff))))

(defn slurp-bike-points
  "Slurps all the bike points from the tfl api"
  []
  (json/read-str (slurp "https://api.tfl.gov.uk/BikePoint")
                 :key-fn #(keyword %)))
  ;(json/read-str (:body (client/get "https://api.tfl.gov.uk/BikePoint"))))

(defn get-closest-leyton-bike-points
  [lat lon]
  (->> (slurp-bike-points)
       (map #(assoc %
                    :h-sqr (get-hypotenuse-squared lat (bigdec (:lat %)) lon (bigdec (:lon %)))
                    :available-bikes (first (for [coll (:additionalProperties %) :when (= (:key coll) "NbBikes")] (:value coll)))))
       (map #(into {} (for [[k v] (select-keys % [:id :url :commonName :lat :lon :h-sqr :available-bikes])] [(keyword k) v])))
       (sort-by :h-sqr)
       (take 5)))

; (defn get-closest-leyton-bike-points
;   [lat lon]
;   (let [all-points (json/read-str (slurp "https://api.tfl.gov.uk/BikePoint"))
;         all-points-cleaned (into [] (map #(into {}
;                                             (for [[k v] (select-keys % ["id" "url" "commonName" "lat" "lon"])]
;                                                  [(keyword k) v])))
;                                       all-points)]
;     all-points-cleaned))

(def fake-users
  #{{:user "Andrew" :password "let-me-in"}
    {:user "Jon" :password "open-says-me"}})

(defn valid-user
  [user password]
  (contains? fake-users {:user user :password password}))

(def svr
  (yada/listener
    ["/" [["boris-bikes" (yada/resource
            ; {:access-control
            ;  {:scheme :cookie
            ;   :cookie "session"
            ;   :verify (fn [cookie] …}}
            {:methods
             {:get
              {:produces "application/json"
               :response (get-closest-leyton-bike-points
                           (:lat leyton-orient-fc)
                           (:lon leyton-orient-fc))}}})]
          ["" (yada/resource
            {:methods
             {:post
              {:consumes "application/x-www-form-urlencoded"
               :parameters {:form
                            {:user s/Str :password s/Str}}
               :response
               (fn [ctx]
                 (let [{:keys [user password]} (get-in ctx [:parameters :form])]
                   (if (valid-user user password)
                     (assoc (:response ctx)
                            :cookies {"session"
                                      {:value
                                       (jwt/sign {:user user} "lp0fTc2JMtx8")}})
                     "Try again.")))}
               :get
               {:produces "text/html"
                :response (html
                            [:form {:method :post}
                              [:label {:for "user"}
                                "Username:"
                                [:input {:name "user" :type :text}]]
                              [:br]
                              [:label {:for "password"}
                                "Password:"
                                [:input {:name "password" :type :password}]]
                              [:br]
                              [:input {:type :submit}]])}}})]]]
    {:port 3000}))

(comment
  ((:close svr))

  (get-closest-leyton-bike-points
    (:lat leyton-orient-fc)
    (:lon leyton-orient-fc)))
