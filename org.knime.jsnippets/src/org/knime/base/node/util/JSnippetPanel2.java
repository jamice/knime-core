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
 *   May 16, 2018 (Johannes Schweig): created
 */
package org.knime.base.node.util;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.text.JTextComponent;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.knime.base.node.preproc.stringmanipulation.manipulator.Manipulator;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.util.SharedIcons;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.ext.sun.nodes.script.expression.Expression;

/**
 * An alternative, lightweight version of {@link JSnippetPanel} designed for the {@link FormulasNodeDialog}.
 * @author Johannes Schweig
 * @since 3.6
 */
public class JSnippetPanel2 extends JPanel {

    private JTextComponent m_expEdit;
    private KnimeCompletionProvider m_completionProvider;
    // A JPanel with CardLayout to switch between the editor and a placeholder
    private JPanel m_cardsPanel;
    // MenuBar to insert columns, flow variables and functions into the editor
    private EditorMenuBar m_menuBar;
    private ManipulatorProvider m_manipProvider;

    // Constants for CardLayout
    private static final String PLACEHOLDER = "placeholder";
    private static final String EDITOR = "editor";

    // Parent NodeDialogPane
    private NodeDialogPane m_ndp;

    /**
     * {@link ListCellRenderer} to display the DisplayName of {@link Manipulator}s in a {@link JList}
     *
     * @author Johannes Schweig
     */
    private class ManipulatorListCellRenderer extends DefaultListCellRenderer {

        /**
         * {@inheritDoc}
         */
        @Override
        public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected,
            final boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            assert (c == this);
            if (value instanceof Manipulator) {
                setText(((Manipulator) value).getDisplayName());
            } else {
                setText(value.toString());
            }
            setFont(new Font(getFont().getName(), Font.PLAIN, getFont().getSize()));
            return this;
        }
    }

    /**
     * {@link JMenuItem} that renders {@link DataColumnSpec}, {@link FlowVariable} and Strings.
     *
     * @author Johannes Schweig
     */
    private class ColumnFlowVarMenuItem extends JMenuItem {

        ColumnFlowVarMenuItem (final Object o) {
            if (o instanceof DataColumnSpec) {
                DataColumnSpec value = (DataColumnSpec) o;
                setText(value.getName());
                setIcon(value.getType().getIcon());
            } else if (o instanceof FlowVariable) {
                FlowVariable value = (FlowVariable) o;
                setText(value.getName());
                Icon icon;
                switch (value.getType()) {
                    case DOUBLE:
                        icon = SharedIcons.FLOWVAR_DOUBLE.get();
                        break;
                    case INTEGER:
                        icon = SharedIcons.FLOWVAR_INTEGER.get();
                        break;
                    case STRING:
                        icon = SharedIcons.FLOWVAR_STRING.get();
                        break;
                    default:
                        icon = SharedIcons.TYPE_DEFAULT.get();

                }
                setIcon(icon);
            } else if (o instanceof String) {
                setText((String)o);
            } else { //fallback
                setText(o.toString());
            }
            setFont(new Font(getFont().getName(), Font.PLAIN, getFont().getSize()));
        }

    }

    /**
     * A JMenuBar with three menus for columns, flow variables and functions
     *
     * @author Johannes Schweig
     */
    private class EditorMenuBar extends JMenuBar {

        // Menus: columns, flow variables, functions (manipulators)
        private JMenu m_columnsMenu;
        private JMenu m_flowVarsMenu;
        private JMenu m_functionsMenu;
        // FunctionsDialog
        private JDialog m_functionsDialog;
        private JList<Manipulator> m_functionsListView;
        // Lists of entries
        private ArrayList<Object> m_columnsList = new ArrayList<Object>();
        private ArrayList<FlowVariable> m_flowVarsList = new ArrayList<FlowVariable>();
        private ArrayList<Manipulator> m_functionsList = new ArrayList<Manipulator>();

        // MenuListener for menus other than functions
        private final MenuListener closeMenuListener = new MenuListener() {

                @Override
                public void menuSelected(final MenuEvent e) {
                    toggleFunctionsDialog(false);
                }

                @Override
                public void menuDeselected(final MenuEvent e) { }

                @Override
                public void menuCanceled(final MenuEvent e) { }
        };

        // MenuListener for functions menu
        private final MenuListener functionsMenuListener = new MenuListener() {

                @Override
                public void menuSelected(final MenuEvent e) {
                    toggleFunctionsDialog(true);
                }

                @Override
                public void menuDeselected(final MenuEvent e) {
                    if (!m_functionsListView.hasFocus()) {
                        toggleFunctionsDialog(false);
                    }
                }

                @Override
                public void menuCanceled(final MenuEvent e) { }
        };

        // ListSelectionListener for the functions menu. Keeps the functions Menu selected while the user selects entries in the list
        private final ListSelectionListener functionsListSelectionListener = new ListSelectionListener() {

            @Override
            public void valueChanged(final ListSelectionEvent e) {
                // keep Menu selected while list gets selected
                m_functionsMenu.setSelected(true);

            }
        };

        /**
         *  KeyAdapter for the list of functions in the functions dialog
         *
         * @author Johannes Schweig
         */
        private class FunctionsListKeyAdapter extends KeyAdapter {

            @Override
            public void keyTyped(final KeyEvent e) {
                // find first entry starting with typed character after the selected entry
                String key = String.valueOf(e.getKeyChar());
                int index = -1;
                for (int i = 0; i < m_functionsListView.getModel().getSize(); i++) {
                    // start search at next list entry
                    int delta = (i + m_functionsListView.getSelectedIndex() + 1) % m_functionsListView.getModel().getSize();
                    String s = m_functionsListView.getModel().getElementAt(delta).getName();
                    if (s.toLowerCase().startsWith(key)){
                        index = delta;
                        break;
                    }
                }
                // if a entry is found, select it and scroll the view
                if (index != -1) {
                    m_functionsListView.setSelectedIndex(index);
                    m_functionsListView.scrollRectToVisible(new Rectangle(m_functionsListView.getCellBounds(index, index)));
                }
            }

            @Override
            public void keyReleased(final KeyEvent e) {
                // close dialog on ESC
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    toggleFunctionsDialog(false);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) { // paste manipulator if ENTER
                    onSelectionInManipulatorList(m_functionsListView.getSelectedValue());
                }
            }

        }

        /**
         * FocusAdapter for the list of functions. Hides the functions dialog when the functions list loses focus.
         *
         * @author Johannes Schweig
         */
        private class FunctionsListFocusAdapter extends FocusAdapter {

                @Override
                public void focusLost(final FocusEvent e) {
                    toggleFunctionsDialog(false);
                }

        }
        /**
         * MouseAdapter for the list of functions. On Double click the function is pasted into the editor.
         *
         * @author Johannes Schweig
         */
        private class FunctionsListMouseAdapter extends MouseAdapter {

            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    onSelectionInManipulatorList(m_functionsListView.getSelectedValue());
                }
            }
        }

        EditorMenuBar() {
            super();
            m_columnsMenu = (JMenu) getPlainComponent(new JMenu("col"));
            m_columnsMenu.setIcon(SharedIcons.ADD_PLUS.get());
            //FIXME allow arrow traversal for menuItems
            m_columnsMenu.setMnemonic(KeyEvent.VK_C);
            m_columnsMenu.addMenuListener(closeMenuListener);
            m_flowVarsMenu = (JMenu) getPlainComponent(new JMenu("fvar"));
            m_flowVarsMenu.setIcon(SharedIcons.ADD_PLUS.get());
            m_flowVarsMenu.setMnemonic(KeyEvent.VK_V);
            m_flowVarsMenu.addMenuListener(closeMenuListener);
            m_functionsMenu = (JMenu) getPlainComponent(new JMenu("func"));
            m_functionsMenu.setIcon(SharedIcons.ADD_PLUS.get());
            //TODO mnemonic not working consistently
            m_functionsMenu.setMnemonic(KeyEvent.VK_F);
            m_functionsMenu.addMenuListener(functionsMenuListener);
            add(m_columnsMenu);
            add(m_flowVarsMenu);
            add(m_functionsMenu);
        }

        /**
         * Toggles the visibility of the functions dialog depending on parameter visible. The dialog is initialized on first use.
         * @param visible if true makes the dialog visible, otherwise hides it.
         */
        private void toggleFunctionsDialog(final boolean visible) {
            // initialize dialog on first use
            if (m_functionsDialog == null) {
                initFunctionsDialog();
            }
            // places the dialog beneath the functions menu
            m_functionsDialog.setBounds((int) m_functionsMenu.getLocationOnScreen().getX(), (int) m_functionsMenu.getLocationOnScreen().getY() + m_functionsMenu.getHeight(), 400, 150);
            m_functionsDialog.pack();
            // show/hide
            m_functionsDialog.setVisible(visible);
            m_functionsMenu.setSelected(visible);
            // focus textfield when hiding functionsDialog
            if (!visible) {
                m_expEdit.requestFocus();
            }
        }

        /**
         * Initializes the functions dialog. Also adds the manipulators from a ManipulationProvider.
         */
        private void initFunctionsDialog() {
            // figure out the parent to be able to make the dialog modal
            m_functionsDialog = new JDialog(getFrame(m_ndp), "", false);
            //        m_jd.setLocationRelativeTo(m_menuBar);
            m_functionsDialog.setUndecorated(true);
            //TODO click on panel deselects the menu and leads to weird refresh on menu click
            // components
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(8, 8, 8, 8));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.anchor = GridBagConstraints.BASELINE_LEADING;
            panel.add(getPlainComponent(new JLabel("Functions"), 2), c);
            c.gridy++;
            c.insets = new Insets(0, 0, 8, 8);
            m_functionsListView = (JList) getPlainComponent(new JList<Manipulator>());
            m_functionsListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            m_functionsListView.setCellRenderer(new ManipulatorListCellRenderer());
            m_functionsListView.addKeyListener(new FunctionsListKeyAdapter());
            m_functionsListView.addFocusListener(new FunctionsListFocusAdapter());
            m_functionsListView.getSelectionModel().addListSelectionListener(functionsListSelectionListener);
            m_functionsListView.addMouseListener(new FunctionsListMouseAdapter());
            DefaultListModel<Manipulator> model = new DefaultListModel<Manipulator>();
            // update functions
            Collection<? extends Manipulator> manipulators = m_manipProvider.getManipulators(ManipulatorProvider.ALL_CATEGORY);
            // sort alphabetically
            Stream<? extends Manipulator> manipStream = manipulators.stream().sorted((m1, m2) -> m1.getDisplayName().toLowerCase().compareTo(m2.getDisplayName().toLowerCase()));
            manipStream.forEach(m -> {
                model.addElement(m);
                m_functionsList.add(m);
            });
            m_functionsListView.setModel(model);
            panel.add(new JScrollPane(m_functionsListView), c);
            c.gridx++;
            c.gridy = 0;
            c.insets = new Insets(0, 0, 8, 4);
            panel.add(new JLabel(SharedIcons.HELP.get()), c);
            c.gridx++;
            c.insets = new Insets(0, 0, 8, 0);
            panel.add(getPlainComponent(new JLabel("Help"), 2), c);
            c.gridx = 1;
            c.gridy++;
            c.gridwidth = 2;
            c.insets = new Insets(0, 0, 0, 0);
            c.fill = GridBagConstraints.BOTH;
            c.weightx = 1;
            c.weighty = 1;
            JLabel helpText = (JLabel) getPlainComponent(new JLabel(""));
            panel.add(helpText, c);

            // change help text when changing function selection
            m_functionsListView.addListSelectionListener(e -> {
                // restrict width to cause text to wrap
                helpText.setText("<html><body width='200px'>" + model.get(m_functionsListView.getSelectedIndex()).getDescription() + "</body></html>");
            });
            m_functionsListView.setSelectionInterval(0, 0);

            m_functionsDialog.add(panel);
        }


        /**
         * Sets the menu entries for the columns menu
         * @param spec DataTableSpec containing the columns
         * @param list additional list with values, e.g. ROWID
         */
        private void updateColumns(final DataTableSpec spec, final String[] list) {
            m_columnsList.clear();
            m_columnsMenu.removeAll();
            for (String s : list) {
                m_columnsList.add(s);
                ColumnFlowVarMenuItem menuItem = new ColumnFlowVarMenuItem(s);
                menuItem.addActionListener(e -> onSelectionInColumnList(s));
                m_columnsMenu.add(menuItem);
            }
            for (int i = 0; i < spec.getNumColumns(); i++) {
                DataColumnSpec colSpec = spec.getColumnSpec(i);
                m_columnsList.add(colSpec);
                ColumnFlowVarMenuItem menuItem = new ColumnFlowVarMenuItem(colSpec);
                menuItem.addActionListener(e -> onSelectionInColumnList(colSpec));
                m_columnsMenu.add(menuItem);
            }

        }

        /**
         * Sets the menu entries for the flow variables menu
         * @param list list of entries to replace the current entries
         */
        private void updateFlowVars(final Collection<FlowVariable> list) {
            m_flowVarsList.clear();
            m_flowVarsMenu.removeAll();
            for (FlowVariable v : list) {
                m_flowVarsList.add(v);
                ColumnFlowVarMenuItem menuItem = new ColumnFlowVarMenuItem(v);
                menuItem.addActionListener(e -> onSelectionInVariableList(v));
                m_flowVarsMenu.add(menuItem);
            }
        }

        /**
         * @return a list with the flow variables in the menu
         */
        public ArrayList<FlowVariable> getFlowVarsList() {
            return m_flowVarsList;
        }

        /**
         * @return a list of all columns in the menu
         */
        public ArrayList<Object> getColList() {
            return m_columnsList;
        }
    }

    /**
     * See {@link JSnippetPanel#JSnippetPanel(ManipulatorProvider, KnimeCompletionProvider)}
     */
    public JSnippetPanel2(final ManipulatorProvider manipulatorProvider,
        final KnimeCompletionProvider completionProvider) {
        this(manipulatorProvider, completionProvider, true);
    }

    /**
     * See {@link JSnippetPanel#JSnippetPanel(ManipulatorProvider, KnimeCompletionProvider, boolean)}
     */
    public JSnippetPanel2(final ManipulatorProvider manipulatorProvider,
        final KnimeCompletionProvider completionProvider, final boolean showColumns) {
        this(manipulatorProvider, completionProvider, showColumns, true);
    }

    /**
     * See {@link JSnippetPanel#JSnippetPanel(ManipulatorProvider, KnimeCompletionProvider, boolean, boolean)}
     */
    public JSnippetPanel2(final ManipulatorProvider manipulatorProvider, final KnimeCompletionProvider completionProvider,
        final boolean showColumns, final boolean showFlowVariables) {
        m_manipProvider = manipulatorProvider;
        m_completionProvider = completionProvider;

        initUI();
        initCompletionProvider();
    }

    /**
     * Initializes the completion provider.
     */
    private void initCompletionProvider() {
        Collection<? extends Manipulator> manipulators = m_manipProvider.getManipulators(ManipulatorProvider.ALL_CATEGORY);
        for (Manipulator m : manipulators) {
            Completion completion = new BasicCompletion(m_completionProvider, m.getName(), m.getDisplayName(), m.getDescription());
            m_completionProvider.addCompletion(completion);
        }
    }

    /**
     * Initializes the user interface.
     */
    private void initUI() {
        // Init GUI
        setLayout(new GridBagLayout());
        // Heading (always displayed)
        JPanel headingPanel = new JPanel(new GridBagLayout());
        JLabel heading = new JLabel("Expression Editor");
        heading.setFont(new Font(getFont().getName(), Font.PLAIN, getFont().getSize() + 2));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(16, 0, 4, 0);
        headingPanel.add(heading, c);

        JPanel editorPanel = new JPanel(new GridBagLayout());
        // Menubar
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 4, 0);
        m_menuBar = new EditorMenuBar();
        editorPanel.add(m_menuBar, c);
        // Editor Textfield
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 3;
        c.weightx = 1;
        c.weighty = 1;
        c.insets = new Insets(4, 0, 8, 0);
        c.fill = GridBagConstraints.BOTH;
        //TODO focus does not always return to Editorcomponent
        m_expEdit = createEditorComponent();
        JScrollPane jspExpEdit = new JScrollPane(m_expEdit);
        jspExpEdit.setPreferredSize(new Dimension(m_expEdit.getPreferredSize().width, 50));
        editorPanel.add(jspExpEdit, c);
        // Evaluate
        c.gridy++;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        editorPanel.add(getPlainComponent(new JLabel("Evaluate on first row")), c);
        c.gridy++;
        editorPanel.add(getPlainComponent(new JLabel("...")), c);
        c.gridx++;
        editorPanel.add(getPlainComponent(new JButton("Evaluate")), c);

        // Placeholder
        JPanel placeholderPanel = new JPanel(new GridBagLayout());
        JLabel placeholder = new JLabel("No expression selected.");
        placeholder.setFont(new Font(getFont().getName(), Font.PLAIN, getFont().getSize()));
        placeholder.setForeground(Color.GRAY);
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        placeholderPanel.add(placeholder, c);
        c.weightx = 1;
        c.weighty = 1;
        c.gridx++;
        c.gridy++;
        placeholderPanel.add(new JLabel(), c);

        // CardLayout to switch between placeholder and editor
        m_cardsPanel = new JPanel(new CardLayout());
        m_cardsPanel.add(placeholderPanel, PLACEHOLDER);
        m_cardsPanel.add(editorPanel, EDITOR);

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.weighty = 0;
        add(headingPanel, c);
        c.gridy++;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        add(m_cardsPanel, c);
    }


    /**
     * See {@link JSnippetPanel#getExpression()}
     */
    public String getExpression() {
        return m_expEdit.getText();
    }

    /**
     * See {@link JSnippetPanel#onSelectionInColumnList(Object)}
     */
    protected void onSelectionInColumnList(final Object selected) {
        String enter;
        if (selected instanceof String) {
            enter = "$$" + selected + "$$";
        } else {
            DataColumnSpec colSpec = (DataColumnSpec)selected;
            String name = colSpec.getName().replace("$", "\\$");
            enter = m_completionProvider.escapeColumnName(name);
        }
        m_expEdit.replaceSelection(enter);
        m_expEdit.requestFocus();
    }

    /**
     * See {@link JSnippetPanel#onSelectionInVariableList(Object)}
     */
    protected void onSelectionInVariableList(final Object selected) {
        if (selected instanceof FlowVariable) {
            FlowVariable v = (FlowVariable)selected;
            String typeChar;
            switch (v.getType()) {
                case DOUBLE:
                    typeChar = "D";
                    break;
                case INTEGER:
                    typeChar = "I";
                    break;
                case STRING:
                    typeChar = "S";
                    break;
                default:
                    return;
            }
            String enter =
                    m_completionProvider.escapeFlowVariableName(typeChar
                            + v.getName()/*.replace("\\", "\\\\").replace("}", "\\}")*/);
            m_expEdit.replaceSelection(enter);
            m_expEdit.requestFocus();
        }

    }

    /**
     * See {@link JSnippetPanel#onSelectionInManipulatorList(Object)}
     */
    protected void onSelectionInManipulatorList(final Manipulator manipulator) {
        String selectedString = m_expEdit.getSelectedText();
        StringBuilder newStr = new StringBuilder(manipulator.getName());
        newStr.append('(');
        for (int i = 0; i < manipulator.getNrArgs(); i++) {
            newStr.append(i > 0 ? ", " : "");
            if (i == 0 && selectedString != null) {
                newStr.append(selectedString);
            }
        }
        newStr.append(')');

        m_expEdit.replaceSelection(newStr.toString());
        if (manipulator.getNrArgs() > 0 && selectedString == null) {
            int caretPos = m_expEdit.getCaretPosition();
            m_expEdit.setCaretPosition(1 + m_expEdit.getText().indexOf('(',
                caretPos - newStr.toString().length()));
        }

        m_expEdit.requestFocus();
    }

    /**
     * See {@link JSnippetPanel#update(String, DataTableSpec, Map)}
     */
    public void update(final String expression, final DataTableSpec spec, final Map<String, FlowVariable> flowVariables) {
        // we have Expression.VERSION_2X
        m_expEdit.setText(expression);
        update(spec, flowVariables);

    }

    /**
     * Updates the contents of the panel with new values.
     *
     * @param spec the data table spec of the input table; used for filling the
     *            list of available columns
     * @param flowVariables a map with all available flow variables; used to
     *            fill the list of available flow variables
     * @since 3.6
     */
    public void update(final DataTableSpec spec, final Map<String, FlowVariable> flowVariables) {
        final String[] expressions = new String[] {Expression.ROWID, Expression.ROWINDEX, Expression.ROWCOUNT};
        m_menuBar.updateColumns(spec, expressions);
        m_menuBar.updateFlowVars(flowVariables.values());

        m_completionProvider.setColumns(spec);
        m_completionProvider.setFlowVariables(flowVariables.values());

    }

    /**
     * See {@link JSnippetPanel#setExpressions(String)}
     */
    public void setExpressions(final String expression) {
        m_expEdit.setText(expression);
    }

    /**
     * See {@link JSnippetPanel#setExpEdit(JTextComponent)}
     */
    protected void setExpEdit(final JTextComponent expEdit) {
        this.m_expEdit = expEdit;
    }

    /**
     * See {@link JSnippetPanel#getCompletionProvider()}
     */
    protected KnimeCompletionProvider getCompletionProvider() {
        return m_completionProvider;
    }

    /**
     * See {@link JSnippetPanel#getFlowVarsList()}
     */
    protected ArrayList<FlowVariable> getFlowVarsList() {
        return m_menuBar.getFlowVarsList();
    }

    /**
     * See {@link JSnippetPanel#getExpEdit()}
     */
    protected JTextComponent getExpEdit() {
        return m_expEdit;
    }

    /**
     * See {@link JSnippetPanel#getColList()}
     */
    protected ArrayList<Object>  getColList() {
        return m_menuBar.getColList();
    }

	/**
     * Creates the text editor component along with the JScrollpane.
     *
     * @return The {@link RSyntaxTextArea} wrapped within a {@link JScrollPane}.
     * @since 2.8
     */
    protected JTextComponent createEditorComponent() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);

        // An AutoCompletion acts as a "middle-man" between a text component
        // and a CompletionProvider. It manages any options associated with
        // the auto-completion (the popup trigger key, whether to display a
        // documentation window along with completion choices, etc.). Unlike
        // CompletionProviders, instances of AutoCompletion cannot be shared
        // among multiple text components.
        AutoCompletion ac = new AutoCompletion(m_completionProvider);
        ac.setShowDescWindow(true);

        ac.install(textArea);

        setExpEdit(textArea);
        return textArea;
    }

    /**
     * Sets a DocumentListener for the text editor component.
     * @param dl
     */
    public void setDocumentListener(final DocumentListener dl) {
        m_expEdit.getDocument().addDocumentListener(dl);
    }

    /**
     * Whether to display the editor panel (false) or a placeholder (true)
     * @param b
     */
    public void setPlaceholder(final boolean b) {
        CardLayout cl = (CardLayout)(m_cardsPanel.getLayout());
        if (b) {
            cl.show(m_cardsPanel, PLACEHOLDER);
        } else {
            cl.show(m_cardsPanel, EDITOR);
        }
    }

    /**
     * Returns the component with plain font weight and larger or smaller font size.
     * @param c the passed component
     * @param s number controlling the font size (positive increases font size, negative decreases font size)
     * @return the component with plain font weight
     */
    public JComponent getPlainComponent(final JComponent c, final int ...s) {
        if (s.length > 0) {
            c.setFont(new Font(getFont().getName(), Font.PLAIN, getFont().getSize() + s[0]));
        } else {
            c.setFont(new Font(getFont().getName(), Font.PLAIN, getFont().getSize()));
        }
        return c;
    }

    /**
     * Sets the {@link NodeDialogPane} for this Panel. This is important to instantiate the functions dialog on the right {@link Frame}.
     * @param ndp
     */
    public void setNodeDialogPane(final NodeDialogPane ndp) {
        m_ndp = ndp;
    }

    /**
     * @param ndp a NodeDialogPane
     * @return the frame of the NodeDialogPane
     */
    private static Frame getFrame(final NodeDialogPane ndp) {
        Frame f = null;
		Container cont = ndp.getPanel().getParent();
		while (cont != null) {
		    if (cont instanceof Frame) {
		        f = (Frame) cont;
		        break;
		    }
		    cont = cont.getParent();
		}
		return f;
    }
}
