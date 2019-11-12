package models

case class QueryFilter(
                         queryString: String,
                         start: Int,
                         end: Int,
                         roles:Seq[String] = Nil,
                         interests:Seq[String] = Nil
                         )
