package plugin2.actions;

import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.FieldDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.expr.AssignExpr;
import japa.parser.ast.expr.BinaryExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.FieldAccessExpr;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import plugin2.Activator;

/**
 * Sample code template for editor context menu
 * and editor text selection or file text processing.
 * @author augustli
 */
public class DoStuff implements IObjectActionDelegate {
	private static final String TITLE = "Do Stuff";
	private Shell shell;

	private IEditorPart editorPart;
	private IDocument doc;
	
	private String selectedText = null;

	private int loggingStatementsPrinted;

	/**
	 * Constructor
	 */
	public DoStuff() {
		super();
	}

	/**
	 * Get the shell object for use in prompting error message.
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	/**
	 * Get the selection text or text for whole file
	 * and pass it to process for modification.
	 * @see IActionDelegate#run(IAction)
	 */
	@Override
	public void run(IAction action) {
		try {
			loggingStatementsPrinted = 0;
			
			editorPart = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()
					.getActivePage().getActiveEditor();

			if (editorPart instanceof AbstractTextEditor) {

				findSelectedText();

				 findDocument();

				CompilationUnit compUnit = JavaParser.parse(new ByteArrayInputStream(doc.get(0, doc.getLength()).getBytes()));

				new MethodVisitor().visit(compUnit, null);

			}
		} catch (Exception e) {
			MessageDialog.openError(shell, TITLE, e.getMessage());
		}
	}

	private void findSelectedText() {
		IEditorSite iEditorSite = editorPart.getEditorSite();
		if (iEditorSite != null) {
			ISelectionProvider selectionProvider = iEditorSite.getSelectionProvider();
			if (selectionProvider != null) {
				ISelection iSelection = selectionProvider.getSelection();
				if (!iSelection.isEmpty()) {
					selectedText = ((ITextSelection) iSelection).getText();
				}
			}
		}
	}
	
	private void findDocument() {
		ITextEditor editor = (ITextEditor) editorPart;
		IDocumentProvider dp = editor.getDocumentProvider();
		doc = dp.getDocument(editor.getEditorInput());
	}

	public void printLog(ArrayList<String> vars, int endLineIndex){
		endLineIndex += loggingStatementsPrinted - 1;
		
		try {
			int lineOffset = doc.getLineOffset(endLineIndex); 

			int endLineLength = doc.getLineLength(endLineIndex);

			String endLine = doc.get(lineOffset, endLineLength);
			
			String textToInsert = buildTextToInsert(vars, endLineLength, endLine);

			lineOffset += endLineLength;

			doc.replace(lineOffset, 0, textToInsert);//insert the text into document

		} catch (Exception e) {
			MessageDialog.openError(shell, TITLE, e.getMessage());
		}
		
		loggingStatementsPrinted++;
	}

	private String buildTextToInsert(ArrayList<String> vars, int endLineLength, String endLine) {
		String textToInsert = "PyxisLog.e(Constants.PYXIS_LOG_TAG, ";
		
		for(int z = 0; z < vars.size(); z++){
			String var = vars.get(z);
			if(z > 0)
				textToInsert += " + ";
			textToInsert += "\" " + var + " = \" + " + var;
		}
		
		textToInsert += ");\n";
		
		for(int z = 0; z < endLineLength; z++){
			if(endLine.charAt(z) == '\t')
				textToInsert = "\t" + textToInsert;
		}
		return textToInsert;
	}

	private class MethodVisitor extends VoidVisitorAdapter {

		/**
		 * Attempt to log all statements within Methods
		 */
		@Override
		public void visit(MethodDeclaration n, Object arg) {
			attemptToLogStatements(n.getBody().getStmts(), n.getName());
		}
		
		/**
		 * Attempt to log all statements within Constructors
		 */
		@Override
		public void visit(ConstructorDeclaration n, Object arg) {
			attemptToLogStatements(n.getBlock().getStmts(), n.getName());
		}
		
		/**
		 * Attempt to log all statements in a list
		 * @param stmts - List of Statements that will attempt to be logged
		 * @param methodName - name of method that the Statements are in
		 */
		private void attemptToLogStatements(List<Statement> stmts, String methodName){
			Iterator<Statement> iterator = stmts.iterator();
			while(iterator.hasNext()){
				Statement stmt = iterator.next();
				if(stmt instanceof ExpressionStmt){
					Expression exp = ((ExpressionStmt)stmt).getExpression();
					breakDownExpressionAndAttemptToLog(exp, methodName);
				}
			}
		}
		
		/**
		 * Attempt to log an Expression
		 * @param exp - variable declaration that will attempt to be logged
		 * @param methodName - name of method that the expression is in
		 */
		private void breakDownExpressionAndAttemptToLog(Expression exp, String methodName){
			if(shouldLog(exp.toString(), methodName)){
				ArrayList<String> vars = new ArrayList<String>();
				addExpressionParts(vars, exp);
				
				printLog(vars, exp.getEndLine());
			}
		}
		
		/**
		 * Should this Expression be logged?
		 * @param exp - expression's text
		 * @param methodName - name of method that the expression is in
		 */
		private boolean shouldLog(String exp, String methodName) {
			return selectedText.contains(exp) || selectedText.contains(methodName);
		}

		
		/**
		 * Add parts of an Expression to a List
		 * @param vars - list to add parts to
		 * @param exp - expression to get parts from
		 */
		private void addExpressionParts(ArrayList<String> vars, Expression exp) {
			if(exp instanceof NameExpr){
				vars.add(exp.toString());
			}
			else if(exp instanceof BinaryExpr){
				BinaryExpr binaryExpr = (BinaryExpr)exp;
				Expression left = binaryExpr.getLeft();
				Expression right = binaryExpr.getRight();
				addExpressionParts(vars, left);
				addExpressionParts(vars, right);
			}
			else if(exp instanceof AssignExpr){
				AssignExpr assignExpr = (AssignExpr)exp;
				vars.add(assignExpr.getTarget().toString());
				addExpressionParts(vars, assignExpr.getValue());
			} 
			else if(exp instanceof VariableDeclarationExpr) {
				List<VariableDeclarator> varDecs = ((VariableDeclarationExpr)exp).getVars();
				ListIterator<VariableDeclarator> iterator = varDecs.listIterator();
				while(iterator.hasNext()){
					VariableDeclarator dec = iterator.next();
					vars.add(dec.getId().toString());
					addExpressionParts(vars, dec.getInit());
				}
			}
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {}

}

