package plugin2.actions;

import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
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
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
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
	private Bounds selectedTextBounds;
	
	private boolean selectedIsClass = false;
	private boolean selectedIsMethod = false;
	
	private String currentClassName = null;
	private String currentMethodName = null;

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

				
				findDocument();
				findSelectedText();

				
				CompilationUnit compUnit = JavaParser.parse(new StringBufferInputStream(doc.get(0, doc.getLength())), "UTF-8");
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
					selectedTextBounds = new Bounds( ((ITextSelection) iSelection));
				}
			}
		}
	}
	
	private void findDocument() {
		ITextEditor editor = (ITextEditor) editorPart;
		IDocumentProvider dp = editor.getDocumentProvider();
		doc = dp.getDocument(editor.getEditorInput());
	}
	
	/**
	 * Attempt to log all statements in a list
	 * @param stmts - List of Statements that will attempt to be logged
	 */
	private void attemptToLogStatements(List<Statement> stmts){
		Iterator<Statement> iterator = stmts.iterator();
		while(iterator.hasNext()){
			Statement stmt = iterator.next();
			if(stmt instanceof ExpressionStmt){
				Expression exp = ((ExpressionStmt)stmt).getExpression();
				breakDownExpressionAndAttemptToLog(exp);
			}
		}
	}
	
	/**
	 * Attempt to log an Expression
	 * @param exp - variable declaration that will attempt to be logged
	 */
	private boolean breakDownExpressionAndAttemptToLog(Expression exp){
		boolean shouldLogMatch = shouldLogMatch(exp);
		boolean shouldLogMethodOrClass = shouldLogMethodOrClass(exp);
		if(shouldLogMatch || shouldLogMethodOrClass){
			ArrayList<String> vars = new ArrayList<String>();
			addExpressionParts(vars, exp);
			
			printLog(vars, exp.getEndLine()-1, shouldLogMatch);
			return true;
		}
		return false;
	}
	
	/**
	 * Should this Expression be logged?
	 * @param exp - expression's text
	 */
	private boolean shouldLogMatch(Expression exp) {
		String expString = exp.toString();
		return ((selectedText.contains(expString) || expString.contains(selectedText)) && selectedTextBounds.overlaps(new Bounds(exp)));
	}
	
	private boolean shouldLogMethodOrClass(Expression exp) {
		return (selectedText.equals(currentMethodName) && selectedIsMethod) || 
				(selectedText.equals(currentClassName) && selectedIsClass);
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

	/**
	 * Create a logging statement and insert it into the document
	 * @param vars - variables to include in the logging statement
	 * @param endLineIndex - the index of the line that the logging statement should follow
	 */
	public void printLog(ArrayList<String> vars, int endLineIndex, boolean continueFilter){
		endLineIndex += loggingStatementsPrinted;
		
		try {
			int lineOffset = doc.getLineOffset(endLineIndex); 

			int endLineLength = doc.getLineLength(endLineIndex);

			String endLine = doc.get(lineOffset, endLineLength);
			
			if(continueFilter)
				vars = stripVars(vars);
			
			String textToInsert = buildTextToInsert(vars, endLineLength, endLine);

			lineOffset += endLineLength;

			doc.replace(lineOffset, 0, textToInsert);//insert the text into document

		} catch (Exception e) {
			MessageDialog.openError(shell, TITLE, e.getMessage());
		}
		
		loggingStatementsPrinted++;
	}

	private ArrayList<String> stripVars(ArrayList<String> vars) {
		ArrayList<String> stripped = new ArrayList<String>();
		for(int z = 0; z < vars.size(); z++){
			if(selectedText.contains(vars.get(z)) && !stripped.contains(vars.get(z)))
				stripped.add(vars.get(z));
		}
		return stripped;
	}

	/**
	 * Build a logging statement
	 * @param vars - variables to include in the logging statement=
	 * @param endLineLength - length of the line that the logging statement should follow
	 * @param endLine - the text of the line that the logging statement should follow
	 */
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
			currentMethodName = n.getName();
			selectedIsMethod = n.getBeginLine() - 1 == selectedTextBounds.startRow && n.getEndLine() - 1 == selectedTextBounds.endRow;
			attemptToLogStatements(n.getBody().getStmts());
			super.visit(n, arg);
		}
		
		/**
		 * Attempt to log all statements within Constructors
		 */
		@Override
		public void visit(ConstructorDeclaration n, Object arg) {
			currentMethodName = n.getName();
			selectedIsMethod = n.getBeginLine() - 1 == selectedTextBounds.startRow && n.getEndLine() - 1 == selectedTextBounds.endRow;
			attemptToLogStatements(n.getBlock().getStmts());
			super.visit(n, arg);
		}
		
		@Override
		public void visit(ClassOrInterfaceDeclaration n, Object arg) {
			currentClassName = n.getName();
			selectedIsClass = n.getBeginLine() - 1 == selectedTextBounds.startRow && n.getEndLine() - 1 == selectedTextBounds.endRow;
			super.visit(n, arg);
		}
		
		/*@Override
		public void visit(VariableDeclarationExpr n, Object arg) {
			if(!breakDownExpressionAndAttemptToLog(n))
				super.visit(n, arg);
		}
		
		@Override
		public void visit(AssignExpr n, Object arg) {
			if(!breakDownExpressionAndAttemptToLog(n))
				super.visit(n, arg);
		}
		
		@Override
		public void visit(BinaryExpr n, Object arg) {
			if(!breakDownExpressionAndAttemptToLog(n))
				super.visit(n, arg);
		}
		
		@Override
		public void visit(NameExpr n, Object arg) {
			if(!breakDownExpressionAndAttemptToLog(n))
				super.visit(n, arg);
		}*/
	}
	
	class Bounds {
		public int startRow;
		public int endRow;
		//public int startColumn;
		//public int endColumn;
		
		public Bounds(int startRow, int endRow, int startColumn, int endColumn) {
			this.startRow = startRow;
			this.endRow = endRow;
			//this.startColumn = startColumn;
			//this.endColumn = endColumn;
		}

		public Bounds(ITextSelection iTextSelection) {
			//try {
				this.startRow = iTextSelection.getStartLine();
				this.endRow = iTextSelection.getEndLine();
				//this.startColumn = iTextSelection.getOffset() - doc.getLineOffset(startRow);
				//this.endColumn = startColumn + iTextSelection.getLength();
			//}
			//catch (BadLocationException e) {
				// TODO Auto-generated catch block
			//	e.printStackTrace();
			//}
		}

		public Bounds(Expression exp) {
			this.startRow = exp.getBeginLine()-1;
			this.endRow = exp.getEndLine()-1;
			//this.startColumn = exp.getBeginColumn()-1;
			//this.endColumn = exp.getEndColumn()-1;
		}
		
		public boolean overlaps(Bounds bounds) {
			/*return (startRow >= bounds.startRow && startRow <= bounds.endRow) ||
					(endRow >= bounds.startRow && endRow <= bounds.endRow) ||
					(startRow <= bounds.startRow && startRow >= bounds.endRow) ||
					(endRow <= bounds.startRow && endRow >= bounds.endRow);*/
			return (bounds.startRow >= startRow && bounds.startRow <= endRow) || (bounds.endRow >= startRow && bounds.endRow <= endRow) || (startRow >= bounds.startRow && startRow <= bounds.endRow) || (endRow >= bounds.startRow && endRow <= bounds.endRow);
		}
	}
	
	@Override
	public void selectionChanged(IAction action, ISelection selection) {}

}

	



