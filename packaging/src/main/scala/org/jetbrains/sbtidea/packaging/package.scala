package org.jetbrains.sbtidea

import java.io.File

import org.jetbrains.sbtidea.packaging.PackagingKeys.ShadePattern
import org.jetbrains.sbtidea.packaging.PackagingKeys.ExcludeFilter
import org.jetbrains.sbtidea.packaging.PackagingKeys.ExcludeFilter._

package object packaging {

  private[packaging] object MAPPING_KIND extends Enumeration {
    type MAPPING_KIND = Value
    val TARGET, LIB, LIB_ASSEMBLY, MISC, UNDEFINED = Value
  }

  private[packaging] case class MappingMetaData(shading: Seq[ShadePattern], excludeFilter: ExcludeFilter, static: Boolean, project: Option[String], kind: MAPPING_KIND.MAPPING_KIND)
  private[packaging] object     MappingMetaData { val EMPTY: MappingMetaData = MappingMetaData(Seq.empty, ExcludeFilter.AllPass, static = true, project = None, kind = MAPPING_KIND.UNDEFINED) }

  private[packaging] case class Mapping(from: File, to: File, metaData: MappingMetaData)

  type Mappings = Seq[Mapping]

  class SkipEntryException extends Exception

  implicit def MappingOrder[A <: Mapping]: Ordering[A] = Ordering.by(x => x.from -> x.to) // order by target jar file

}
