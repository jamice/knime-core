/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2011
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
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
 *  propagated with or for interoperation with KNIME. The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ------------------------------------------------------------------------
 * 
 * History
 *   Mar 30, 2011 (wiswedel): created
 */
package org.knime.core.node.workflow.virtual.parallelbranchend;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

import org.knime.core.data.DataRow;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.workflow.LoopEndParallelizeNode;
import org.knime.core.node.workflow.NodeID;
import org.knime.core.node.workflow.NodeStateChangeListener;
import org.knime.core.node.workflow.NodeStateEvent;
import org.knime.core.node.workflow.virtual.ParallelizedBranchContent;

/**
 * 
 * @author wiswedel, University of Konstanz
 */
public class ParallelBranchEndNodeModel extends NodeModel
implements LoopEndParallelizeNode,
NodeStateChangeListener {

    /* Store map of end node IDs and corresponding branch objects */
    private LinkedHashMap<NodeID, ParallelizedBranchContent> m_branches;
    

	/**
	 * @param outPortTypes
	 */
	ParallelBranchEndNodeModel() {
		super(new PortType[]{ BufferedDataTable.TYPE},
		        new PortType[]{ BufferedDataTable.TYPE});
	}
	
    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        // spec of the one chunk arriving here is representative for the
        // entire table.
        return inSpecs;
    }

    /**
	 * {@inheritDoc}
	 */
	@Override
	protected PortObject[] execute(final PortObject[] inObjects,
	        final ExecutionContext exec)
			throws Exception {
	    // start by copying the results of this branch to the output...
	    BufferedDataTable lastBranch = (BufferedDataTable)inObjects[0];
	    BufferedDataContainer bdc
	            = exec.createDataContainer(lastBranch.getDataTableSpec());
        for (DataRow row : lastBranch) {
            bdc.addRowToTable(row);
        }
	    boolean done = false;
	    while (!done) {
	        // wait a bit
	        try {
	            Thread.sleep(500); 
	        } catch (InterruptedException ie) {
	            // nothing to do, just continue
	        }
	        // check if execution was canceled
	        try {
	            exec.checkCanceled();
	        } catch (CanceledExecutionException cee) {
	            // TODO: cancel all branches
	            throw cee;
	        }
	        // check if any of the branches are finished
	        for (ParallelizedBranchContent pbc : m_branches.values()) {
	            if (pbc.isExecuted()) {
	                // copy results from branch
	                // TODO: try to keep the order...
	                BufferedDataTable bdt
	                        = (BufferedDataTable)pbc.getOutportContent()[0];
	                for (DataRow row : bdt) {
	                    bdc.addRowToTable(row);
	                }
	                pbc.removeAllNodesFromWorkflow();
	                m_branches.remove(pbc.getVirtualOutputID());
	                exec.setProgress((double)m_branches.size()
	                                 /(double)pbc.getBranchCount());
	            }
	            // TODO: also do something with failures and nodes
	            // that do not execute anymore.
	        }
	    }
	    BufferedDataTable result = null;
        if (bdc != null) {
            bdc.close();
            result = bdc.getTable();
        }
        if (result == null) {
            throw new Exception("Something went terribly wrong. We are sorry for any inconvenience this may cause.");
        }
		return new PortObject[] { result };
	}
	
    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // TODO: cancel and delete all branches
        m_branches = new LinkedHashMap<NodeID, ParallelizedBranchContent>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
	public void addParallelBranch(final ParallelizedBranchContent pbc)
	{
	    if (m_branches.containsKey(pbc.getVirtualOutputID())) {
	        throw new IllegalArgumentException("Can't insert branch with duplicate key!");
	    }
	    m_branches.put(pbc.getVirtualOutputID(), pbc);
	    pbc.registerLoopEndStateChangeListener(this);
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		// no settings
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		// no settings
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
			throws InvalidSettingsException {
		// no settings
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File nodeInternDir,
			final ExecutionMonitor exec) throws IOException,
			CanceledExecutionException {
		// no internals
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File nodeInternDir,
			final ExecutionMonitor exec) throws IOException,
			CanceledExecutionException {
		// no internals
	}

	//////////////////////////////////////////
	// NodeStateChangeListener Methods
	//////////////////////////////////////////
	
    /**
     * {@inheritDoc}
     */
    public void stateChanged(NodeStateEvent state) {
        NodeID endNode = state.getSource();
        if (m_branches.containsKey(endNode)) {
            this.notify();
        }
    }

}
