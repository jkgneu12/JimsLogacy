package logacy.actions;

import logacy.Activator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

public class ViewServer extends AbstractHandler implements IObjectActionDelegate {
	
	private static final String onCreate = "onCreate(){";
	private static final String threadPolicy = "\n\t\tif (android.os.Build.VERSION.SDK_INT > 9) {\n\t\t\tStrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();\n\t\t\tStrictMode.setThreadPolicy(policy);\n\t\t}";
	private static final String superOnCreate = "super.onCreate();";
	private static final String addWindow = "\n\t\tViewServer.get(this).addWindow(this);";
	private static final String superOnResume = "super.onResume();";
	private static final String focusWindow = "\n\t\tViewServer.get(this).setFocusedWindow(this);";
	private static final String superOnDestroy = "super.onDestroy();";
	private static final String removeWindow = "\n\t\tViewServer.get(this).removeWindow(this);";

	
	private MessageConsoleStream console;
	private IEditorPart editorPart;
	private IDocument doc;
	
	public ViewServer() {}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		console = Activator.getConsole();
		editorPart = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().getActiveEditor();

		if (editorPart instanceof AbstractTextEditor) {
			ITextEditor editor = (ITextEditor) editorPart;
			IDocumentProvider dp = editor.getDocumentProvider();
			doc = dp.getDocument(editor.getEditorInput());
			
			
			try {
				String docString = doc.get(0, doc.getLength());
				
				FindReplaceDocumentAdapter find = new FindReplaceDocumentAdapter(doc);
				IRegion match = find.find(0, threadPolicy, true, true, false, false);
				
				int logStartIndex;
				int logEndIndex;
				
				if(match != null){
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);
					
					match = find.find(0, addWindow, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);
					
					match = find.find(0, focusWindow, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);
					
					match = find.find(0, removeWindow, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);	
					
				} else {
					
					match = find.find(0, onCreate, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + onCreate + threadPolicy + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);	
					
					match = find.find(0, superOnCreate, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + superOnCreate + addWindow + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);
					
					match = find.find(0, superOnResume, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + superOnResume + focusWindow + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);
					
					match = find.find(0, superOnDestroy, true, true, false, false);
					
					logStartIndex = match.getOffset();
					logEndIndex = logStartIndex + match.getLength();
						
					docString = docString.substring(0, logStartIndex) + superOnDestroy + removeWindow + docString.substring(logEndIndex);
					doc.replace(0, doc.getLength(), docString);
				}	
					
				
				
				
			} catch (BadLocationException e) {
				console.println(e.getStackTrace().toString());
			}
		}
		
		return null;
	}

	@Override
	public void run(IAction action) {
		try {
			execute(null);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			console.println(e.getStackTrace().toString());
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {}

	@Override
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {}
}
