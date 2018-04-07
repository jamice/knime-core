/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
 *   7 Apr 2018 (albrecht): created
 */
package org.knime.core.node.interactive;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;

/**
 * A serializable POJO holding information about the execution status of a view request and
 * after successful execution the generated response object, or a possible error message otherwise.
 *
 * @author Christian Albrecht, KNIME GmbH, Konstanz, Germany
 * @param <RES> the actual class of the response implementation to be generated
 * @since 3.6
 */
public class ViewResponsePromise<RES extends ViewResponse> {

    private ExecutionMonitor m_exec;
    private RES m_response;
    private boolean m_executionFailed;
    private String m_errorMessage;

    /**
     * Creates a new promise object
     * @param exec the {@link ExecutionMonitor} holding progress and cancel information
     */
    public ViewResponsePromise(final ExecutionMonitor exec) {
        m_exec = exec;
    }

    /**
     * The current progress value or null if no progress available.
     * @return Progress value between 0 and 1, or null.
     */
    public double getProgress() {
        return m_exec.getProgressMonitor().getProgress();
    }

    /**
     * The current progress message or null if no message available.
     * @return Progress message or null
     */
    public String getProgressMessage() {
        return m_exec.getProgressMonitor().getMessage();
    }

    /**
     * Checks if the execution of the request was cancelled.
     * @return true if the execution is cancelled, false otherwise
     */
    public boolean isCancelled() {
        try {
            m_exec.checkCanceled();
            return false;
        } catch (CanceledExecutionException ex) {
            return true;
        }
    }

    /**
     * Sets the response object on this promise.
     * @param response the response to set
     */
    public void setResponse(final RES response) {
        m_response = response;
        // execution is finished, progress must be 1
        m_exec.setProgress(1);
    }

    /**
     * Returns a response object or null, if the response is unavailable. The latter might be
     * the case if the generation of the response is not yet complete or an error has occurred.
     * @return the response object or null
     */
    public RES getResponse() {
        return m_response;
    }

    /**
     * @return true if a response object is available, false oherwise
     */
    public boolean isResponseAvailable() {
        return m_response != null;
    }

    /**
     * @return true if an error occurred during the generation of the response object, false otherwise
     */
    public boolean isExecutionFailed() {
        return m_executionFailed;
    }

    /**
     * Set a flag that whether the processing of the view request failed.
     * @param executionFailed true if an error occurred during the generation of the response object, false otherwise
     */
    public void setExecutionFailed(final boolean executionFailed) {
        m_executionFailed = executionFailed;
    }

    /**
     * Returns an error message in case {@link #isExecutionFailed()} yields true and a message was available.
     * @return an error message or null
     */
    public String getErrorMessage() {
        return m_errorMessage;
    }

    /**
     * Sets an optional error message in case the processing of the view request failed.
     * @param errorMessage the error message to set
     */
    public void setErrorMessage(final String errorMessage) {
        m_errorMessage = errorMessage;
    }

}
