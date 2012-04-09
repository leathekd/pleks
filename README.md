# pleks

Pleks is a utility library for working with the Plex Media Server's
HTTP interface.

## Usage

The main function is *visit* which can take either a string (typically
just the url of the Plex server and 32400, the default port) or a map.
The map, in this case, must be either the resulting map from a call to
*visit*, or one of the child nodes from the same call. See the
examples below.

*visit-child* and *visit-in* are more convenient to use once the
 result from the initial call to visit is in hand.

## Examples

```clj
(def root (visit "myserver.local:34200"))
```

This will result in some map similar to:

```clj
{:attr1 "value"
 :etc "value"
 :children [{:key "somekey" :title "Some Key"}
            {:key "someotherkey" :title "Some Other Key"}]}
```

From here, we can either pass the whole thing to *visit* (that
will simply revisit the same node again) or we can visit one of the
children nodes.

```clj
(visit (first (:children root)))
```

Often it is useful to fetch one of the children nodes based on some key.

```clj
;; visit by the value of the :key key
(visit-child root #"somekey")

;; visit by the value of some other key
(visit-child root #"^Some" :title)
```

Finally, it's useful to chain calls to *visit-child* together so
*visit-in* is provided

```clj
(visit-in root [#"library" #"sections"
                #"^TV" {:title #"Firefly"}
                {:title #"Season 1"}
                {:title #"Jaynestown"}])
```

## License

Copyright (C) 2012 David Leatherman

Distributed under the Eclipse Public License, the same as Clojure.
