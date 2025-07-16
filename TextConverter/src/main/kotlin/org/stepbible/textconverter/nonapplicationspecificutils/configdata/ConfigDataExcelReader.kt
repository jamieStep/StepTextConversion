package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import java.io.FileInputStream



/****************************************************************************/
/**
 * Takes config information from stepConfig.xlsx if available.
 *
 * @author ARA "Jamie" Jamieson
 */

object ConfigDataExcelReader: ObjectInterface
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
   * Reads data from stepConfig.xlsx if available.
   *
   * @return A collection of lines, or null.
   */

  fun process (): List<String>?
  {
    /***************************************************************************/
    val filePath = FileLocations.getConfigSpreadsheetFilePath() ?: return null



    /***************************************************************************/
    val res: MutableList<String> = mutableListOf()
    val fis = FileInputStream(filePath)
    val workbook = XSSFWorkbook(fis)
    val sheet = workbook.getSheetAt(0)

    val C_ParameterName_Col = 2
    val C_Value_Col = 3



    /***************************************************************************/
    for (row in sheet)
    {
      if (row.physicalNumberOfCells < C_ParameterName_Col + 1)
        continue

      val parameterName = row.getCell(C_ParameterName_Col).toString().trim()
      if (parameterName.isNotEmpty())
      {
        val value = row.getCell(C_Value_Col).toString().trim()
        if (value.isNotEmpty()) res.add(parameterName + value)
      }
    }

    workbook.close()
    fis.close()

    return res
  }
}