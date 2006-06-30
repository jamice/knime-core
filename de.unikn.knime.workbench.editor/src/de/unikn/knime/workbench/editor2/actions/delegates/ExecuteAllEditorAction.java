/* @(#)$RCSfile$ 
 * $Revision: 174 $ $Date: 2006-02-14 20:34:50 +0100 (Di, 14 Feb 2006) $ $Author: ohl $
 * 
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2004
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   10.11.2005 (Florian Georg): created
 */
package de.unikn.knime.workbench.editor2.actions.delegates;

import de.unikn.knime.workbench.editor2.WorkflowEditor;
import de.unikn.knime.workbench.editor2.actions.AbstractNodeAction;
import de.unikn.knime.workbench.editor2.actions.ExecuteAllAction;

/**
 * Editor action for "execute" all executable nodes.
 * 
 * @author Christoph Sieb, University of Konstanz
 */
public class ExecuteAllEditorAction extends AbstractEditorAction {

    /**
     * @see 
     * de.unikn.knime.workbench.editor2.actions.delegates.AbstractEditorAction
     *      #createAction(de.unikn.knime.workbench.editor2.WorkflowEditor)
     */
    @Override
    protected AbstractNodeAction createAction(final WorkflowEditor editor) {
        return new ExecuteAllAction(editor);
    }

}
