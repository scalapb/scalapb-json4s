# ScalaPB and JSON

Scalapb-json4s can convert protocol buffers to and from JSON, using [json4s](http://json4s.org/).

## Setting up your project

Make sure that you are using ScalaPB 0.5.x or later.

In `build.sbt` add a dependency on `scalapb-json4s`:

```
libraryDepenencies += "com.trueaccord.scalapb" %% "scalapb-json4s" % "0.1.1"
```

In your code, you can now convert to JSON:

```
import com.trueaccord.scalapb.json.JsonFormat

val r: String = JsonFormat.toJsonString(myProto)
```

Parse JSON back to a protocol buffer:

```
import com.trueaccord.scalapb.json.JsonFormat

val proto: MyProto = JsonFormat.fromJsonString[MyProto](
    """{"x": "17"}""")
```

There are lower-level functions `toJson()` and `fromJson()` that convert from protos to json4s’s `JValue`:

```
def toJson(m: GeneratedMessage): JObject

def fromJson[Proto](value: JValue): Proto
```

Finally, in JsonFormat there are two implicit methods that instantiate `Reader[Proto]` and `Writer[Proto]`.
