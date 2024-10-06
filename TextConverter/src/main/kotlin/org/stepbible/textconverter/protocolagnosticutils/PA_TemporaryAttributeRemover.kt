package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.NodeMarker
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning

/******************************************************************************/
/**
 * To simplify processing, I may add temporary attributes to nodes (all of the
 * with names starting '-' so long as I've got things right).
 *
 * I doubt that having these still present at the end of processing would break
 * anything, but it's probably not worth taking the risk.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_TemporaryAttributeRemover
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Removes all temporary attributes.
  *
  * @param dataCollection Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    Rpt.reportWithContinuation(level = 1, "Replacing temporary nodes ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
              NodeMarker.deleteAllMarkers(rootNode)
            } // asyncable
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  } // fun
} // object
