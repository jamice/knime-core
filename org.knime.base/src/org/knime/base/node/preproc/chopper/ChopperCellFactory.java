/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Dec 19, 2007 (ohl): created
 */
package org.knime.base.node.preproc.chopper;

import java.util.Arrays;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.container.AbstractCellFactory;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.def.StringCell.StringCellFactory;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;

/**
 * Creates the data cells for the new columns of the chopper.
 *
 * @author ohl, KNIME
 */
class ChopperCellFactory extends AbstractCellFactory {

    private final DataTableSpec m_inSpec;

    private final String m_targetCol;

    private final int m_colIdx;

    private final String m_newColName;

    private final String m_delimiter;

    private final int m_numOfChops;

    private final DataType m_itemType;

    private final boolean m_keepRest;

    /**
     * Le Constucteur.
     *
     * @param inSpec the spec from the underlying input table
     * @param colName the name of the column to chop things off
     * @param newColName name of the appended vector column
     * @param delimiter delimiter of extracted value tokens
     * @param numOfChops number of elements chopped off from the beginngin of the string value of the cell
     * @param itemType type to convert chopped off items to
     * @param keepRest replaces the content in the selected column with the rest of the chopped of string, otherwise
     *            column is removed
     * @throws InvalidSettingsException if the specified col name is not in the input spec, or if the number of colNames
     *             is different than required
     */
    public ChopperCellFactory(final DataTableSpec inSpec, final String colName, final String newColName,
        final String delimiter, final int numOfChops, final DataType itemType, final boolean keepRest)
            throws InvalidSettingsException {

        m_inSpec = inSpec;

        m_colIdx = m_inSpec.findColumnIndex(colName);
        if (m_colIdx < 0) {
            throw new InvalidSettingsException(
                "Specified column must be contained in input table ('" + colName + "' not found).");
        }
        if (!m_inSpec.getColumnSpec(m_colIdx).getType().isCompatible(StringValue.class)) {
            throw new InvalidSettingsException(
                "Selected column must be of type String (column '" + colName + "' is not).");
        }
        if (!itemType.isASuperTypeOf(DoubleCell.TYPE) && !itemType.isASuperTypeOf(StringCell.TYPE)) {
            throw new InvalidSettingsException("The selected item type ('" + itemType + "') is not supported.");
        }
        m_targetCol = colName;
        m_newColName = newColName;
        m_delimiter = delimiter;
        m_numOfChops = numOfChops;
        m_itemType = itemType;
        m_keepRest = keepRest;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataCell[] getCells(final DataRow row) {
        DataCell[] result = new DataCell[m_keepRest ? m_numOfChops + 1 : m_numOfChops];
        DataCell theRest;
        DataCell col = row.getCell(m_colIdx);
        if (col.isMissing()) {
            Arrays.fill(result, DataType.getMissingCell());
            theRest = DataType.getMissingCell();
        } else {
            String val = ((StringValue)col).getStringValue();
            String[] splits = val.split(m_delimiter, m_numOfChops + 1);
            DataCell missing = m_itemType.isASuperTypeOf(StringCell.TYPE) ? StringCellFactory.create("")
                : DoubleCellFactory.create(Double.NaN);
            for (int i = 0; i < m_numOfChops; i++) {
                if (i < splits.length) {
                    if (m_itemType.isASuperTypeOf(StringCell.TYPE)) {
                        result[i] = StringCellFactory.create(splits[i]);
                    } else {
                        if (splits[i].trim().isEmpty()) {
                            result[i] = DoubleCellFactory.create(Double.NaN);
                        } else {
                            try {
                                result[i] = DoubleCellFactory.create(splits[i]);
                            } catch (NumberFormatException nfe) {
                                throw new RuntimeException("Can't convert '" + splits[i] + "' to floating point. Item #"
                                    + i + " Row " + row.getKey(), nfe);
                            }
                        }
                    }
                } else {
                    result[i] = missing;
                }
            }
            String rest = splits.length <= m_numOfChops ? "" : splits[splits.length - 1];
            theRest = StringCellFactory.create(rest);
        }

        if (m_keepRest) {
            result[m_numOfChops] = theRest;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataColumnSpec[] getColumnSpecs() {
        // add column names to table spec as element names
        String newNamePrefix = m_newColName;
        if (newNamePrefix == null || newNamePrefix.trim().isEmpty()) {
            newNamePrefix = "Label";
        }
        int numofNewCols = m_keepRest ? m_numOfChops + 1 : m_numOfChops;
        DataColumnSpec[] newCols = new DataColumnSpec[numofNewCols];
        for (int i = 0; i < m_numOfChops; i++) {
            newCols[i] = new DataColumnSpecCreator(newNamePrefix + i, m_itemType).createSpec();
        }
        if (m_keepRest) {
            DataColumnSpecCreator strCreator = new DataColumnSpecCreator(m_targetCol, StringCell.TYPE);
            newCols[numofNewCols - 1] = strCreator.createSpec();
        }
        return newCols;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProgress(final long curRowNr, final long rowCount, final RowKey lastKey,
        final ExecutionMonitor exec) {
        exec.setProgress((double)curRowNr / (double)rowCount,
            "processing row #" + curRowNr + " of " + rowCount + " (" + lastKey.getString() + ")");
    }
}
