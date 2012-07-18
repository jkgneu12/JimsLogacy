package logacy.actions;

import japa.parser.JavaParser;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.Parameter;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.expr.AnnotationExpr;
import japa.parser.ast.expr.AssignExpr;
import japa.parser.ast.expr.BinaryExpr;
import japa.parser.ast.expr.ConditionalExpr;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.MethodCallExpr;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.IfStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import java.io.StringBufferInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;

import logacy.Activator;
import logacy.preferences.PreferenceConstants;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorExtension3.InsertMode;


/**
 * Sample code template for editor context menu
 * and editor text selection or file text processing.
 * @author augustli
 */
public class Logger implements IObjectActionDelegate, IHandler {

	private MessageConsoleStream console;
	private IEditorPart editorPart;
	private IDocument doc;
	private String docString;
	
	private String selectedText = null;
	private Bounds selectedTextBounds;
	
	private boolean selectedIsClass = false;
	private boolean selectedIsMethod = false;
	
	private String currentClassName = null;
	private String currentMethodName = null;

	private TreeMap<Integer, Integer> loggingStatementsPrinted;

	private String logLevel;
	
	boolean didReplace;
	private String LOG_CLASS;
	private String LOG_TAG;
	
	

	/**
	 * Constructor
	 */
	public Logger(String logLevel) {
		super();
		this.logLevel = logLevel;
	}

	/**
	 * Get the shell object for use in prompting error message.
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}

	/**
	 * Get the selection text or text for whole file
	 * and pass it to process for modification.
	 * @see IActionDelegate#run(IAction)
	 */
	@Override
	public void run(IAction action) {
		this.LOG_CLASS = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.CLASS);
		this.LOG_TAG = Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.TAG);
		try {
			loggingStatementsPrinted = new TreeMap<Integer, Integer>();
			
			console = Activator.getConsole();
			editorPart = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()
					.getActivePage().getActiveEditor();
		

			if (editorPart instanceof AbstractTextEditor) {
				didReplace = false;
				
				findDocument();
				findSelectedText();

				
				CompilationUnit compUnit = JavaParser.parse(new StringBufferInputStream(doc.get(0, doc.getLength())), "UTF-8");
				new MethodVisitor().visit(compUnit, null);
				
				if(didReplace){
					ISelectionProvider provider =((AbstractTextEditor) editorPart).getSelectionProvider();
					ISelection selection = provider.getSelection();
					doc.replace(0, doc.getLength(), docString);
					provider.setSelection(selection);
				}
				else
					console.println("Could not find any loggable variables in selection");

			}
		}  catch (Exception e){
			console.println(e.getStackTrace().toString());
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
	
	private void findDocument() throws BadLocationException {
		ITextEditor editor = (ITextEditor) editorPart;
		IDocumentProvider dp = editor.getDocumentProvider();
		doc = dp.getDocument(editor.getEditorInput());
		docString = doc.get(0, doc.getLength());
	}
	
	/**
	 * Attempt to log all statements in a list
	 * @param stmts - List of Statements that will attempt to be logged
	 */
	private void attemptToLogStatements(List<Statement> stmts){
		if(stmts != null){
			Iterator<Statement> iterator = stmts.iterator();
			while(iterator.hasNext()){
				Statement stmt = iterator.next();
				if(stmt instanceof ExpressionStmt){
					Expression exp = ((ExpressionStmt)stmt).getExpression();
					breakDownExpressionAndAttemptToLog(exp);
				} 
				else if(stmt instanceof BlockStmt) {
					attemptToLogStatements(((BlockStmt)stmt).getStmts());
				}
			}
		}
	}
	
	/**
	 * Attempt to log an Expression
	 * @param exp - variable declaration that will attempt to be logged
	 */
	private boolean breakDownExpressionAndAttemptToLog(Expression exp){
		boolean shouldLogMatch = shouldLogMatch(exp);
		boolean shouldLogMethodOrClass = shouldLogMethodOrClass();
		if(shouldLogMatch || shouldLogMethodOrClass){
			ArrayList<String> vars = new ArrayList<String>();
			addExpressionParts(vars, exp);
			
			printLog(vars, exp.getEndLine()-1, shouldLogMatch, 0);
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
	
	private boolean shouldLogMethodOrClass() {
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
		} else if (exp instanceof MethodCallExpr){
			Expression scope = ((MethodCallExpr)exp).getScope();
			if(scope != null){
				if(scope.toString().length() > 0 && !Character.isUpperCase(scope.toString().charAt(0)))
					vars.add(scope.toString());
				List<Expression> args = ((MethodCallExpr)exp).getArgs();
				if(args != null){
					Iterator<Expression> iterator = args.iterator();
					while(iterator.hasNext()){
						addExpressionParts(vars, iterator.next());
					}
				}
			}
		} 
		else if(exp instanceof ConditionalExpr){
			addExpressionParts(vars, ((ConditionalExpr)exp).getThenExpr());
			addExpressionParts(vars, ((ConditionalExpr)exp).getElseExpr());
		}
	}

	/**
	 * Create a logging statement and insert it into the document
	 * @param vars - variables to include in the logging statement
	 * @param endLineIndex - the index of the line that the logging statement should follow
	 */
	public void printLog(ArrayList<String> vars, int endLineIndex, boolean continueFilter, int increaseIndent){
		if(vars == null || vars.size() == 0) return;
		
		try {
			int lineOffset = doc.getLineOffset(endLineIndex); 

			int endLineLength = doc.getLineLength(endLineIndex);

			String endLine = doc.get(lineOffset, endLineLength);
			
			if(continueFilter)
				vars = stripVars(vars);
			
			if(vars == null || vars.size() == 0) return;
			
			String textToInsert = buildTextToInsert(vars, endLineLength, endLine, increaseIndent);

			lineOffset += loggingStatementsPrintedBefore(lineOffset);

			String begin = docString.substring(0, lineOffset);
			String middle = docString.substring(lineOffset, lineOffset + endLineLength);
			String end = docString.substring(lineOffset + endLineLength);
			docString = new StringBuilder(begin).append(middle).append(textToInsert).append(end).toString();
			
			
			loggingStatementsPrinted.put(endLineIndex, textToInsert.length());
			didReplace = true;

		} catch (Exception e) {
			console.println(e.getStackTrace().toString());
		}
		
		
	}

	private int loggingStatementsPrintedBefore(int endLineOffset) {
		int count = 0;
		Iterator<Integer> iterator = loggingStatementsPrinted.keySet().iterator();
		while(iterator.hasNext()){
			int n = iterator.next();
			int val = loggingStatementsPrinted.get(n);
			if(val < endLineOffset + count)
				count += val;
		}
		return count;
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
	private String buildTextToInsert(ArrayList<String> vars, int endLineLength, String endLine, int increaseIndent) {
		String textToInsert = LOG_CLASS + "." + logLevel + "(" + LOG_TAG + ", ";
		
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
		for(int z = 0; z < increaseIndent; z++)
			textToInsert = "\t" + textToInsert;
		
		return textToInsert;
	}

	private class MethodVisitor extends VoidVisitorAdapter {
		
		private int getSignatureBeginIndex(String signature, String fullText, int beginIndex){
			String preSignature = fullText.substring(0, fullText.indexOf(signature));
			if(preSignature.contains("\n")){
				String[] lines = preSignature.split("\n");
				return beginIndex + lines.length ;
			}
			return beginIndex;
		}

		/**
		 * Attempt to log all statements within Methods
		 */
		@Override
		public void visit(MethodDeclaration n, Object arg) {
			currentMethodName = n.getName();
			String signature = getSignature(n.toString(), n.getBody(), n.getAnnotations());
			int beginLine = getSignatureBeginIndex(signature, n.toString(), n.getBeginLine() - 1);
			selectedIsMethod = beginLine == selectedTextBounds.startRow; //&& n.getEndLine() - 1 == selectedTextBounds.endRow;
			
			
			
			if(shouldLogMethodOrClass() || selectedText.contains(signature)){
				if(n.getParameters() != null){
					Iterator<Parameter> iterator = n.getParameters().iterator();
					ArrayList<String> vars = new ArrayList<String>(); 
					while(iterator.hasNext())
						vars.add(iterator.next().getId().toString());
					printLog(vars, beginLine, false, 1);
				}
			}
			if(n.getBody() != null)
				attemptToLogStatements(n.getBody().getStmts());
			
			super.visit(n, arg);
		}
		
		private String getSignature(String name, BlockStmt body, List<AnnotationExpr> annotations){
			
			int endIndex;
			if(body == null)
				endIndex = name.length() - 1;
			else
				endIndex = name.indexOf(body.toString());
			
			String preBody = name.substring(0, endIndex).trim();
			
			int lastAnnotationIndex = 0;
			if(annotations != null){
				String lastAnnotation = annotations.get(annotations.size()-1).toString();
				lastAnnotationIndex = preBody.indexOf(lastAnnotation) + lastAnnotation.length();
			}
			
			return preBody.substring(lastAnnotationIndex).trim();
		}
		
		private int lastLineOfSignature(int beginLine){
			try {
				while(!doc.get(doc.getLineOffset(beginLine), doc.getLineLength(beginLine)).contains("{")){
					beginLine++;
				}
			} catch (BadLocationException e) {
				console.println(e.getStackTrace().toString());
			}
			return beginLine;
				
		}
		
		/**
		 * Attempt to log all statements within Constructors
		 */
		@Override
		public void visit(ConstructorDeclaration n, Object arg) {
			currentMethodName = n.getName();
			String signature = getSignature(n.toString(), n.getBlock(), n.getAnnotations());
			int beginLine = getSignatureBeginIndex(signature, n.toString(), n.getBeginLine() - 1);
			selectedIsMethod = beginLine == selectedTextBounds.startRow; //&& n.getEndLine() - 1 == selectedTextBounds.endRow;
			
			
			
			if(shouldLogMethodOrClass() || selectedText.contains(signature)){
				if(n.getParameters() != null){
					Iterator<Parameter> iterator = n.getParameters().iterator();
					ArrayList<String> vars = new ArrayList<String>(); 
					while(iterator.hasNext())
						vars.add(iterator.next().getId().toString());
					
					int lineOffset = lastLineOfSignature(n.getBeginLine()-1);
					int increaseIndent = 1;
					int nextLine = lineOffset+1;
					try {
						if(doc.get(doc.getLineOffset(nextLine), doc.getLineLength(nextLine)).contains("super(")){
							lineOffset++;
							increaseIndent = 0;
						}
					} catch (BadLocationException e) {
						console.println(e.getStackTrace().toString());
					}
					
					printLog(vars, lineOffset, false, increaseIndent);
				}
			}
			
			attemptToLogStatements(n.getBlock().getStmts());
			
			super.visit(n, arg);
		}
		
		@Override
		public void visit(ClassOrInterfaceDeclaration n, Object arg) {
			currentClassName = n.getName();
			selectedIsClass = n.getBeginLine() - 1 == selectedTextBounds.startRow; //&& n.getEndLine() - 1 == selectedTextBounds.endRow;
			super.visit(n, arg);
		}
		
		
		@Override
		public void visit(IfStmt n, Object arg) {
			ArrayList<Statement> statements = new ArrayList<Statement>();
			statements.add(n.getThenStmt());
			statements.add(n.getElseStmt());
			attemptToLogStatements(statements);
			super.visit(n, arg);
		}
	}
	
	class Bounds {
		public int startRow;
		public int endRow;
		
		public Bounds(int startRow, int endRow, int startColumn, int endColumn) {
			this.startRow = startRow;
			this.endRow = endRow;
		}

		public Bounds(ITextSelection iTextSelection) {
			this.startRow = iTextSelection.getStartLine();
			this.endRow = iTextSelection.getEndLine();
		}

		public Bounds(Expression exp) {
			this.startRow = exp.getBeginLine()-1;
			this.endRow = exp.getEndLine()-1;
		}
		
		public boolean overlaps(Bounds bounds) {
			return (bounds.startRow >= startRow && bounds.startRow <= endRow) || (bounds.endRow >= startRow && bounds.endRow <= endRow) || (startRow >= bounds.startRow && startRow <= bounds.endRow) || (endRow >= bounds.startRow && endRow <= bounds.endRow);
		}
	}
	
	@Override
	public void selectionChanged(IAction action, ISelection selection) {}

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {}

	@Override
	public void dispose() {}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		run(null);
		return null;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener handlerListener) {}

}

	



