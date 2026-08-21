package liric.mistaken.scripting.engine.groovy

import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer

/**
 * Un CompilationCustomizer que transforma accesos a propiedades tipo "isXxxx" 
 * en invocaciones de método "isXxxx()".
 * 
 * Esto resuelve el problema donde Groovy lanza MissingPropertyException 
 * para `player.isOnline` o `entity.isDead` porque Bukkit define los métodos como `isOnline()`
 * y Groovy los expone como propiedades `online` (JavaBean spec).
 * 
 * Al hacer la transformación en tiempo de compilación (AST Transformation),
 * no hay sobrecarga en tiempo de ejecución ni fugas de memoria por MetaClasses globales.
 */
class GroovyBukkitCompatibilityCustomizer : CompilationCustomizer(CompilePhase.SEMANTIC_ANALYSIS) {

    override fun call(source: SourceUnit, context: GeneratorContext, classNode: ClassNode) {
        val transformer = object : ClassCodeExpressionTransformer() {
            override fun getSourceUnit(): SourceUnit = source

            override fun transform(exp: Expression?): Expression? {
                if (exp == null) return null

                
                if (exp is PropertyExpression) {
                    val propertyName = exp.propertyAsString
                    
                    
                    if (propertyName != null && propertyName.startsWith("is") && propertyName.length > 2 && propertyName[2].isUpperCase()) {
                        
                        
                        val methodCall = MethodCallExpression(
                            transform(exp.objectExpression), 
                            propertyName,
                            ArgumentListExpression() 
                        )
                        
                        methodCall.setSourcePosition(exp)
                        methodCall.isSafe = exp.isSafe             
                        methodCall.isSpreadSafe = exp.isSpreadSafe 
                        
                        return methodCall
                    }
                }

                return super.transform(exp)
            }
        }

        transformer.visitClass(classNode)
    }
}
