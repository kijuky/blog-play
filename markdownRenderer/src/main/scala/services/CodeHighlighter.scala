package services

trait CodeHighlighter {
  def highlight(code: String, language: Option[String]): Option[String]
  def languageForExtension(ext: String): Option[String]
}
