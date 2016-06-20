package io.mediachain.transactor

object Main {

  def main(args: Array[String]): Unit = {
    Config.parser.parse(args, Config()) match {
      case None => sys.exit(1)
      case Some(c) =>
        c.mode match {
          case TransactorMode => JournalServer.run(c)
          case FacadeMode => RpcService.run(c)
        }
    }
  }

}
