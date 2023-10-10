import java.util.stream.IntStream

fun main(args: Array<String>) {
  val things = listOf(1, 2, 3)
  val ix = IntStream.range(0, things.size - 1).filter { ix: Int -> things[ix] > 1 }.findFirst()
  if (ix.isEmpty) println("No match")
  println("Hello World!")
}