package arch.configs

import arch.system.DeviceDescriptor
import scala.collection.immutable.ListMap
import chisel3.util.BitPat
import java.nio.file.{ Files, Paths }
import java.nio.charset.StandardCharsets

abstract class Config(
  val site: Map[Any, Any] = Map.empty,
  val knobs: Map[String, Any] = Map.empty
) {
  def toInstance(prev: Parameters): Parameters =
    Parameters(site, Some(prev))

  def ++(other: Config): Config =
    new Config(site ++ other.site, knobs ++ other.knobs) {}
}

class View[T](pname: Parameters => T) {
  def apply(p: Parameters): T = pname(p)
}

trait ConfigField {
  def section: String
}

object ConfigDump {

  sealed trait JsonNode
  case class JsonLeaf(value: String)                    extends JsonNode
  case class JsonObj(fields: ListMap[String, JsonNode]) extends JsonNode
  case class JsonArr(items: Seq[JsonNode])              extends JsonNode

  private def toJsonNode(v: Any): JsonNode = v match {
    case s: String              => JsonLeaf(s""""$s"""")
    case b: Boolean             => JsonLeaf(b.toString)
    case i: Int                 => JsonLeaf(i.toString)
    case l: Long                => JsonLeaf(l.toString)
    case d: Double              => JsonLeaf(d.toString)
    case f: Float               => JsonLeaf(f.toString)
    case bit: BitPat            => JsonLeaf(s""""0x${bit.value.longValue.toHexString}"""")
    case opt: Option[_]         => opt.map(toJsonNode).getOrElse(JsonLeaf("null"))
    case seq: Seq[_]            => JsonArr(seq.map(toJsonNode))
    case desc: DeviceDescriptor =>
      JsonObj(
        ListMap(
          "device" -> JsonLeaf(s""""${desc.name}""""),
          "type"   -> JsonLeaf(s""""${desc.deviceType}""""),
          "start"  -> JsonLeaf(s""""0x${desc.startAddr.toHexString}""""),
          "end"    -> JsonLeaf(s""""0x${desc.endAddr.toHexString}""""),
        )
      )
    case m: Map[_, _]           =>
      JsonArr(m.toSeq.map { case (k, v2) =>
        JsonObj(ListMap(k.toString -> toJsonNode(v2)))
      })
    case other                  => JsonLeaf(s""""${other.toString}"""")
  }

  private def render(node: JsonNode, indent: Int = 0): String = {
    val pad   = " " * indent
    val inner = " " * (indent + 2)
    node match {
      case JsonLeaf(v)     => v
      case JsonArr(items)  =>
        val lines = items.map(n => s"$inner${render(n, indent + 2)}")
        s"[\n${lines.mkString(",\n")}\n$pad]"
      case JsonObj(fields) =>
        val lines = fields.map { case (k, n) =>
          s"""$inner"$k": ${render(n, indent + 2)}"""
        }
        s"{\n${lines.mkString(",\n")}\n$pad}"
    }
  }

  private def sectionOf(field: Any): List[String] = field match {
    case f: ConfigField => f.section.split("\\.").toList
    case _              => Nil
  }

  private def fieldSimpleName(field: Any): String =
    field.getClass.getName
      .stripSuffix("$")
      .split("\\$")
      .last

  private def insertIntoObj(
    obj: JsonObj,
    path: List[String],
    key: String,
    node: JsonNode
  ): JsonObj = path match {
    case Nil          =>
      JsonObj(obj.fields + (key -> node))
    case head :: tail =>
      val child = obj.fields.get(head) match {
        case Some(o: JsonObj) => o
        case _                => JsonObj(ListMap.empty)
      }
      JsonObj(obj.fields + (head -> insertIntoObj(child, tail, key, node)))
  }

  def dump(p: Parameters, path: String = "config.json"): Unit = {
    var root: JsonObj = JsonObj(ListMap.empty)

    p.site.foreach { case (field, value) =>
      val name    = fieldSimpleName(field)
      val section = sectionOf(name)
      val node    = toJsonNode(value)
      root = insertIntoObj(root, section, name, node)
    }

    val json = render(root) + "\n"
    Files.write(Paths.get(path), json.getBytes(StandardCharsets.UTF_8))
    println(s"[ConfigDump] Written to $path")
  }
}
