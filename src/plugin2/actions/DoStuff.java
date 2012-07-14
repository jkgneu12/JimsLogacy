package plugin2.actions;

import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
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

	String selectedText = null;
	int selectedTextOffset = 0;
	int length = 0;

	IEditorPart editorPart;
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
			//get active editor
			editorPart = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()
					.getActivePage().getActiveEditor();

			if (editorPart instanceof AbstractTextEditor) {
				//check if there is text selection


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

				String textToReplace = "PyxisLog.e(Constants.PYXIS_LOG_TAG, \"" + selectedText + " = \" + " + selectedText + ");\n";

				length = textToReplace.length();

				ITextEditor editor = (ITextEditor) editorPart;
				IDocumentProvider dp = editor.getDocumentProvider();
				IDocument doc = dp.getDocument(editor.getEditorInput());

				CompilationUnit compUnit = JavaParser.parse(new ByteArrayInputStream(doc.get(0, doc.getLength()).getBytes()));

				new MethodVisitor().visit(compUnit, null);

			}
		} catch (Exception e) {
			MessageDialog.openError(shell, TITLE, e.getMessage());
		}
	}

	public void printLog(ArrayList<String> vars, int endLineIndex){
		endLineIndex += loggingStatementsPrinted - 1;
		try {
			String textToReplace = "PyxisLog.e(Constants.PYXIS_LOG_TAG, ";
			
			for(int z = 0; z < vars.size(); z++){
				String var = vars.get(z);
				if(z > 0)
					textToReplace += " + ";
				textToReplace += "\" " + var + " = \" + " + var;
			}
			
			textToReplace += ");\n";

			length = textToReplace.length();

			ITextEditor editor = (ITextEditor) editorPart;
			IDocumentProvider dp = editor.getDocumentProvider();
			IDocument doc = dp.getDocument(editor.getEditorInput());


			int offset = doc.getLineOffset(endLineIndex); 

			int endLineLength = doc.getLineLength(endLineIndex);

			String endLine = doc.get(offset, endLineLength);

			int tabs = 0;
			for(int z = 0; z < endLineLength; z++){
				if(endLine.charAt(z) == '\t')
					textToReplace = "\t" + textToReplace;

			}

			offset += doc.getLineLength(endLineIndex);

			doc.replace(offset, 0, textToReplace);

		} catch (Exception e) {
			MessageDialog.openError(shell, TITLE, e.getMessage());
		}
		loggingStatementsPrinted++;
	}

	private class MethodVisitor extends VoidVisitorAdapter {

		@Override
		public void visit(MethodDeclaration n, Object arg) {
			// here you can access the attributes of the method.
			// this method will be called for all methods in this 
			// CompilationUnit, including inner class methods
			System.out.println("MethodDeclaration " + n.getName());
			
			BlockStmt body = n.getBody();
			iterateOverStatements(body.getStmts());
		}
		
		private void iterateOverStatements(List<Statement> stmts){
			Iterator<Statement> iterator = stmts.iterator();
			while(iterator.hasNext()){
				Statement stmt = iterator.next();
				if(stmt instanceof ExpressionStmt){
					Expression exp = ((ExpressionStmt)stmt).getExpression();
					if(exp instanceof VariableDeclarationExpr){
						parse((VariableDeclarationExpr)exp);
					} else if (exp instanceof AssignExpr){
						parse((AssignExpr)exp);
					}
				}
			}
		}

		@Override
		public void visit(FieldAccessExpr n, Object arg) {
			// TODO Auto-generated method stub
			super.visit(n, arg);
		}

		@Override
		public void visit(FieldDeclaration n, Object arg) {
			// TODO Auto-generated method stub
			super.visit(n, arg);
			parse(n);
		}
		
		@Override
		public void visit(VariableDeclarationExpr n, Object arg) {
			// TODO Auto-generated method stub
			super.visit(n, arg);
			parse(n);
			
		}

		@Override
		public void visit(ExpressionStmt n, Object arg) {
			// TODO Auto-generated method stub
			super.visit(n, arg);
		}
		
		@Override
		public void visit(AssignExpr n, Object arg) {
			// TODO Auto-generated method stub
			super.visit(n, arg);
			parse(n);
		}
		
		@Override
		public void visit(VariableDeclarator n, Object arg) {
			// TODO Auto-generated method stub
			super.visit(n, arg);
		}
		
		private void parse(FieldDeclaration exp){
			if(selectedText.contains(exp.toString())){
				iterateOverVars(exp.getVariables(), exp.getEndLine());
			}
		}
		
		private void parse(VariableDeclarationExpr exp){
			if(selectedText.contains(exp.toString())){
				iterateOverVars(exp.getVars(), exp.getEndLine());
			}
		}
		
		private void parse(AssignExpr exp){
			if(selectedText.contains(exp.toString())){
				ArrayList<String> vars = new ArrayList<String>();
				addExpParts(vars, exp.getTarget());
				addExpParts(vars, exp.getValue());
				printLog(vars, exp.getEndLine());
			}
		}

		private void iterateOverVars(List<VariableDeclarator> varDecs, int endLineIndex){
			ArrayList<String> vars = new ArrayList<String>();
			
			ListIterator<VariableDeclarator> iterator = varDecs.listIterator();
			while(iterator.hasNext()){
				VariableDeclarator dec = iterator.next();
				vars.add(dec.getId().toString());
				Expression exp = dec.getInit();
				addExpParts(vars, exp);
			}
			printLog(vars, endLineIndex);
		}
		
		private void addExpParts(ArrayList<String> vars, Expression exp) {
			if(exp instanceof NameExpr){
				vars.add(exp.toString());
			}
			else if(exp instanceof BinaryExpr){
				Expression left = ((BinaryExpr)exp).getLeft();
				Expression right = ((BinaryExpr)exp).getRight();
				addExpParts(vars, left);
				addExpParts(vars, right);
			}
			else if(exp instanceof AssignExpr){
				vars.add(((AssignExpr)exp).getTarget().toString());
				addExpParts(vars, ((AssignExpr)exp).getValue());
			}
		}
	}



	/**
	 * Skip implementation
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	@Override
	public void selectionChanged(IAction action, ISelection selection) {
	}

}

