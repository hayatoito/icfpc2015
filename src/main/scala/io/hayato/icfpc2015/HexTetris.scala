package io.hayato.icfpc2015

import scala.annotation.tailrec
import scala.io.Source
import scala.math

import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser
import spray.json._
import spray.json.DefaultJsonProtocol._

object HexTetris extends LazyLogging {

  sealed trait GameState
  case object GamePlaying extends GameState
  case object GameOver extends GameState

  def nextSeed(seed: Int): Int = seed * 1103515245 + 12345
  def randomNumber(seed: Int): Int = (seed >> 16) & ((1 << 15) - 1)

  case class Cell(x: Int, y: Int) {
    def toHex = Hex(e = x - y / 2, se = y)
  }

  case class UnitConfig(members: Seq[Cell], pivot: Cell) {
    def toBlockConfig = BlockConfig(members.map(_.toHex), pivot.toHex)
  }

  case class Problem(id: Int, units: Seq[UnitConfig], width: Int, height: Int, filled: Seq[Cell], sourceLength: Int, sourceSeeds: Seq[Int]) {
    def initialBoard = Board(width, height, IndexedSeq.fill(height, width)(false)).fill(filled)
    def blocks: Seq[Block] = units.map(unitConfig => {
      val cells = unitConfig.members
      val topMost = cells.map(_.y).min
      val leftMost = cells.map(_.x).min
      val rightMost = cells.map(_.x).max
      Block(unitConfig.toBlockConfig, Cell((width - rightMost - 1 - leftMost) / 2, -topMost).toHex)
    })
    def blockForSeed(seed: Int): Block = blocks(randomNumber(seed) % sourceLength)
    def gameFor(seed: Int) = Game(GamePlaying, Nil, BoardBlock(initialBoard, blockForSeed(seed)), seed, blocks, sourceLength - 1, 0, 0)
  }

  case class Output(problemId: Int, seed: Int, tag: String, solution: String)

  case class GameResult(problem: Problem, seed: Int, game: Game) {
    def toOutput = Output(problemId = problem.id, seed = seed, tag = "hayato", solution = game.moveHistory.reverse.map(move => move.chars(0)).mkString(""))
  }
  case class CommandLineOptions(inputFiles: Seq[String] = Seq(), timeLimit: Int = Int.MaxValue, memoryLimit: Int = Int.MaxValue, numProcessors: Int = 1, phaseOfPower: String = "",
    replayFile: String = "", outputFile: String = "")

  sealed trait Move { def chars: Seq[Char] }
  case object MoveW extends Move { def chars = Seq('p', '\'', '!', '.', '0', '3') }
  case object MoveE extends Move { def chars = Seq('b', 'c', 'e', 'f', 'y', '2') }
  case object MoveSW extends Move { def chars = Seq('a', 'g', 'h', 'i', 'j', '4') }
  case object MoveSE extends Move { def chars = Seq('l', 'm', 'n', 'o', ' ', '5') }
  case object RotateClockwise extends Move { def chars = Seq('d', 'q', 'r', 'v', 'z', '1') }
  case object RotateCounterClockwise extends Move { def chars = Seq('k', 's', 't', 'u', 'w', 'x') }

  val allMoves = Seq(MoveE, MoveW, MoveSE, MoveSW, RotateClockwise, RotateCounterClockwise)

  case class Hex(e: Int, se: Int) {
    def toCell: Cell = Cell(e + (se / 2), se)
    def +(o: Hex): Hex = Hex(e + o.e, se + o.se)
    def -(o: Hex): Hex = Hex(e - o.e, se - o.se)
    def rotateClockwise: Hex = Hex(-se, e + se)
  }

  case class BlockConfig(members: Seq[Hex], pivot: Hex) {
    val N = 6
    private def rotateClockwise(hexes: Seq[Hex]) = hexes.map(hex => (hex - pivot).rotateClockwise + pivot)
    private val rotations: Seq[Seq[Hex]] = (0 to N).foldLeft(List(members))((xs, _) => rotateClockwise(xs.head) :: xs).reverse
    def hexes(rotation: Int): Seq[Hex] = rotations(rotation)
  }

  case class Block(blockConfig: BlockConfig, pos: Hex, rotation: Int = 0) {
    def rotateClockwise = this.copy(rotation = (rotation + blockConfig.N) % blockConfig.N)
    def rotateCounterClockwise = this.copy(rotation = (rotation + blockConfig.N - 1) % blockConfig.N)

    def move(move: Move): Block = move match {
      case MoveW => this.copy(pos = pos.copy(e = pos.e - 1))
      case MoveE => this.copy(pos = pos.copy(e = pos.e + 1))
      case MoveSW => this.copy(pos = pos.copy(e = pos.e - 1, se = pos.se + 1))
      case MoveSE => this.copy(pos = pos.copy(se = pos.se + 1))
      case RotateClockwise => rotateClockwise
      case RotateCounterClockwise => rotateCounterClockwise
    }

    def size: Int = blockConfig.members.size
    def hexes: Seq[Hex] = blockConfig.hexes(rotation).map(_ + pos)
    def cells: Seq[Cell] = hexes.map(_.toCell)
    def pivot: Cell = (blockConfig.pivot + pos).toCell
  }

  case class Board(width: Int, height: Int, full: IndexedSeq[IndexedSeq[Boolean]]) {
    def empty(cell: Cell): Boolean = !full(cell.y)(cell.x)
    def canPut(block: Block): Boolean = block.cells.forall { case Cell(x, y) => 0 <= x && x < width && 0 <= y && y < height } && block.cells.forall { cell => empty(cell) }
    def fill(block: Block): Board = fill(block.cells)
    def fill(cell: Cell): Board = this.copy(full = full.updated(cell.y, full(cell.y).updated(cell.x, true)))
    def fill(cells: Seq[Cell]): Board = cells.foldLeft(this)((b, cell) => b.fill(cell))
    def clearLines: BoardLocked = {
      val remainedLines = full.filter { row => !row.forall { f => f }}
      val clearedLines = height - remainedLines.size
      BoardLocked(this.copy(full = IndexedSeq.fill(clearedLines, width)(false) ++ remainedLines), clearedLines)
    }
    def e: Int = (0 until height).map(y => full(y).count { t => t } * (height - y)).sum * -1
  }

  case class BoardLocked(board: Board, clearedLines: Int)

  case class BoardBlock(board: Board, block: Block) {
    def valid: Boolean = board.canPut(block)
    def moveBlock(move: Move): Either[BoardBlock, BoardLocked] = {
      val nextBlock = block.move(move)
      if (board.canPut(nextBlock)) Left(this.copy(board, block = nextBlock))
      else Right(board.fill(block).clearLines)
    }
    override def toString: String = {
      (for {
        y <- 0 until board.height
        row = board.full(y)
      } yield {
        val line = (0 until board.width).map(x => {
          if (row(x)) "O"
          else if (block.cells.contains(Cell(x, y))) "X"
          else "."
        }).mkString(" ")
        (if (y % 2 == 0) "" else " ") + line
      }).mkString("\n")
    }
  }

  case class Game(gameState: GameState, moveHistory: List[Move], boardBlock: BoardBlock, seed: Int, blocks: Seq[Block], sources: Int, score: Int, oldLines: Int) {
    def move(move: Move): Game = {
      val newHistory = move :: moveHistory
      boardBlock.moveBlock(move) match {
        case Left(nextBoardBlock) => this.copy(moveHistory = newHistory, boardBlock = nextBoardBlock)
        case Right(BoardLocked(nextBoard, clearedLines)) => {
          val points = boardBlock.block.size + 100 * (1 + clearedLines) * clearedLines / 2
          val lineBonus  = if (oldLines > 1) ((oldLines - 1) * points / 10) else 0
          val newScore = score + points + lineBonus
          if (sources == 0) this.copy(gameState = GameOver, moveHistory = newHistory, score = newScore)
          else {
            val s = nextSeed(seed)
            val newBlock = blocks(randomNumber(s) % blocks.size)
            if (nextBoard.canPut(newBlock)) this.copy(moveHistory = newHistory, boardBlock = BoardBlock(nextBoard, newBlock), seed = s, sources = sources - 1, oldLines = clearedLines, score = newScore)
            else this.copy(gameState = GameOver, moveHistory = newHistory, boardBlock = BoardBlock(nextBoard, newBlock), seed = s, oldLines = clearedLines, score = newScore)
          }
        }
      }
    }
    override def toString: String = s"game: ${gameState} score: ${score} move: ${moveHistory.size} sources: ${sources}, board:\n${boardBlock}"
  }

  trait Algorithm {
    def solve(game: Game): Game
  }

  trait IterativeAlgorithm extends Algorithm {
    def solve(game: Game): Game = solve(game, this)
    def options: CommandLineOptions = CommandLineOptions()
    def next(game: Game): (Game, IterativeAlgorithm)
    @tailrec
    final def solve(game: Game, a: IterativeAlgorithm): Game = game.gameState match {
      case GamePlaying => a.next(game) match { case (ng, na) => solve(ng, na)}
      case GameOver => game
    }
  }

  case class RandomMover(random: scala.util.Random) extends IterativeAlgorithm {
    def next(game : Game): (Game, IterativeAlgorithm) = {
      val move = allMoves(random.nextInt(allMoves.size))
      (game.move(move), RandomMover(random))
    }
  }

  case class HueristicMover() extends IterativeAlgorithm {
    case class State(pos: Hex, rotation: Int)
    def toState(game: Game): State = toState(game.boardBlock)
    def toState(boardBlock: BoardBlock): State = State(boardBlock.block.pos, boardBlock.block.rotation)
    case class SearchResult(eScore: Int, game: Game)
    def next(game: Game): (Game, IterativeAlgorithm) = bfs(Set[State](), List(game), None) match {
      case Some(SearchResult(_, g)) => (g, this)
      case None => (game.copy(gameState = GameOver), this)
    }

    @tailrec
    final def bfs(visited: Set[State], queue: List[Game], best: Option[SearchResult]): Option[SearchResult] = queue match {
      case Nil => best
      case h :: t => {
        val s = toState(h)
        if (visited(s)) bfs(visited, t, best)
        else {
          val newBest: Option[SearchResult] = allMoves.foldLeft(best)((best, move) => h.boardBlock.moveBlock(move) match {
            case Left(_) => best
            case Right(BoardLocked(board, _)) => best match {
              case None => Some(SearchResult(board.e, h.move(move)))
              case Some(SearchResult(e, g)) => if (e < board.e) Some(SearchResult(board.e, h.move(move))) else best
            }
          })
          val nextGames: List[Game] = allMoves.flatMap(move => h.boardBlock.moveBlock(move) match {
            case Left(_) => Some(h.move(move))
            case Right(_) => None
          }).toList
          val nextVisited = visited + s
          bfs(nextVisited,  nextGames.filter(game => !nextVisited(toState(game))) ++: queue, newBest)
        }
      }
    }
  }

  def main(args : Array[String]): Unit = {

    object JsonProtocol extends DefaultJsonProtocol {
      implicit lazy val cellFormat = jsonFormat2(Cell)
      implicit lazy val unitSourceFormat = jsonFormat2(UnitConfig)
      implicit lazy val problemFormat = jsonFormat7(Problem)
      implicit lazy val outputFormat = jsonFormat4(Output)
    }

    val parser = new OptionParser[CommandLineOptions]("tetris") {
      opt[String]('f', "inputFile") unbounded() action { (x, c) => c.copy(inputFiles = c.inputFiles :+ x)}
      opt[Int]('t', "timeLimitInSeconds") action { (x, c) => c.copy(timeLimit = x)}
      opt[Int]('m', "memoryLimitInMegaBytes") action { (x, c) => c.copy(memoryLimit = x)}
      opt[Int]('c', "numProcessors") action { (x, c) => c.copy(numProcessors = x)}
      opt[String]('p', "phaseOfPower") action { (x, c) => c.copy(phaseOfPower = x)}
      opt[String]('r', "replayFile") action { (x, c) => c.copy(replayFile = x)}
      opt[String]('o', "outputFile") action { (x, c) => c.copy(outputFile = x)}
    }

    parser.parse(args, CommandLineOptions()) match {
      case Some(options) => {
        import JsonProtocol._
        if (!options.replayFile.isEmpty()) {
            val replay = Source.fromFile(options.replayFile).mkString.parseJson.convertTo[Output]
          assert(!options.inputFiles.isEmpty)
          val problem = Source.fromFile(options.inputFiles(0)).mkString.parseJson.convertTo[Problem]
          replayGame(problem, replay)
        } else {
          val output = for {
            gameResults <- options.inputFiles.map(inputFile => runProblem(Source.fromFile(inputFile).mkString.parseJson.convertTo[Problem], options))
            gameResult <- gameResults
          } yield gameResult.toOutput
          if (options.outputFile.isEmpty()) println(output.toJson.toString)
          else writeToFile(options.outputFile, output.toJson.toString)
        }
      }
      case None => logger.error("Bad command line options")
    }
  }

  def writeToFile(fileName: String, contents: String): Unit = {
    import java.nio.file.{Paths, Files}
    Files.write(Paths.get(fileName), contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  def replayGame(problem: Problem, replay: Output): Game = {
    val commandToMove: Map[Char, Move] =
      (for {
        move <- allMoves
        char <- move.chars
      } yield (char, move)).toMap
    logger.info(s"Replay problem: ${replay.problemId}")
    assert(replay.problemId == problem.id)
    val game = problem.gameFor(replay.seed)
    val gameover = replay.solution.foldLeft(game)((game, command) => game.move(commandToMove(command)))
    logger.info(s"Replay is over: score: ${gameover}")
    gameover
  }

  def runProblem(problem: Problem, options: CommandLineOptions): Seq[GameResult] = {
    logger.info(s"Run problem: ${problem.id}")
    val gameResults =
      for {
        seed <- problem.sourceSeeds
      } yield {
        val game = problem.gameFor(seed)
        logger.info(s"Game is staring: ${game}")
        val gameOver = HueristicMover().solve(game)
        logger.info(s"Game is over: ${gameOver}")
        GameResult(problem, seed, gameOver)
      }
    val scores = gameResults.map(r => r.game.score)
    logger.info(s"scores: ${scores}")
    logger.info(s"totalScore: ${scores.sum}")
    gameResults
  }
}
