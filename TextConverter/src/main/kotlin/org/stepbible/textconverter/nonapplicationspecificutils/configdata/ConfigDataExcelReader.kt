package org.stepbible.textconverter.nonapplicationspecificutils.configdata

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
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

  fun process (): Pair<Float, List<String>>
  {
    /***************************************************************************/
    val formatter = DataFormatter()
    val filePath = FileLocations.getConfigSpreadsheetFilePath()!!



    /***************************************************************************/
    val lines: MutableList<String> = mutableListOf()
    var templateVersion = "1.0"
    val fis = FileInputStream(filePath)
    val workbook = XSSFWorkbook(fis)
    val sheet = workbook.getSheetAt(0)

    val C_ParameterName_Col = 2
    val C_Value_Col = 3



    /***************************************************************************/
    for (row in sheet)
    {
      if (row.physicalNumberOfCells > 0)
      {
        val x = row.getCell(0)?.toString()?.trim() ?: ""
        if (x.startsWith("Template version"))
        {
          templateVersion = x.replaceFirst("Template version ", "").trim()
          continue
        }
      }

      if (row.physicalNumberOfCells < C_ParameterName_Col + 1)
        continue

      val parameterName = row.getCell(C_ParameterName_Col).toString().trim()
      if (parameterName.isNotEmpty())
      {
        val value = formatter.formatCellValue(row.getCell(C_Value_Col)).trim()
        if (value.isNotEmpty()) lines.add(parameterName + value)
      }
    }

    workbook.close()
    fis.close()

    return Pair(templateVersion.toFloat(), lines)
  }
}