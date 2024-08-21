/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.lsp.ast

import org.pkl.core.parser.antlr.PklParser.*
import org.pkl.lsp.*
import org.pkl.lsp.LSPUtil.firstInstanceOf
import org.pkl.lsp.resolvers.ResolveVisitor
import org.pkl.lsp.resolvers.ResolveVisitors
import org.pkl.lsp.resolvers.Resolvers
import org.pkl.lsp.type.Type
import org.pkl.lsp.type.TypeParameterBindings
import org.pkl.lsp.type.computeExprType
import org.pkl.lsp.type.computeThisType

class PklThisExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ThisExprContext,
) : AbstractNode(project, parent, ctx), PklThisExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitThisExpr(this)
  }
}

class PklOuterExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: OuterExprContext,
) : AbstractNode(project, parent, ctx), PklOuterExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitOuterExpr(this)
  }
}

class PklModuleExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ModuleExprContext,
) : AbstractNode(project, parent, ctx), PklModuleExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitModuleExpr(this)
  }
}

class PklNullLiteralExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: NullLiteralContext,
) : AbstractNode(project, parent, ctx), PklNullLiteralExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNullLiteralExpr(this)
  }
}

class PklTrueLiteralExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: TrueLiteralContext,
) : AbstractNode(project, parent, ctx), PklTrueLiteralExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTrueLiteralExpr(this)
  }
}

class PklFalseLiteralExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: FalseLiteralContext,
) : AbstractNode(project, parent, ctx), PklFalseLiteralExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFalseLiteralExpr(this)
  }
}

class PklIntLiteralExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: IntLiteralContext,
) : AbstractNode(project, parent, ctx), PklIntLiteralExpr {

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitIntLiteralExpr(this)
  }
}

class PklFloatLiteralExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: FloatLiteralContext,
) : AbstractNode(project, parent, ctx), PklFloatLiteralExpr {

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFloatLiteralExpr(this)
  }
}

class PklThrowExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ThrowExprContext,
) : AbstractNode(project, parent, ctx), PklThrowExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitThrowExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklTraceExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: TraceExprContext,
) : AbstractNode(project, parent, ctx), PklTraceExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTraceExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklImportExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ImportExprContext,
) : AbstractNode(project, parent, ctx), PklImportExpr {
  override val isGlob: Boolean by lazy { ctx.IMPORT_GLOB() != null }

  override val moduleUri: PklModuleUri? by lazy { PklModuleUriImpl(project, this, ctx) }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitImportExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklReadExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ReadExprContext,
) : AbstractNode(project, parent, ctx), PklReadExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val isNullable: Boolean by lazy { ctx.READ_OR_NULL() != null }
  override val isGlob: Boolean by lazy { ctx.READ_GLOB() != null }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitReadExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklUnqualifiedAccessExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: UnqualifiedAccessExprContext,
) : AbstractNode(project, parent, ctx), PklUnqualifiedAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val memberNameText: String by lazy { identifier!!.text }
  override val argumentList: PklArgumentList? by lazy {
    children.firstInstanceOf<PklArgumentList>()
  }
  override val isNullSafeAccess: Boolean = false

  override fun resolve(): Node? {
    val base = project.pklBaseModule
    val visitor = ResolveVisitors.firstElementNamed(memberNameText, base)
    return resolve(base, null, mapOf(), visitor)
  }

  override fun <R> resolve(
    base: PklBaseModule,
    receiverType: Type?,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>,
  ): R {
    return Resolvers.resolveUnqualifiedAccess(
      this,
      receiverType,
      isPropertyAccess,
      base,
      bindings,
      visitor,
    )
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitUnqualifiedAccessExpr(this)
  }
}

class PklSingleLineStringLiteralImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: SingleLineStringLiteralContext,
) : AbstractNode(project, parent, ctx), PklSingleLineStringLiteral {
  override val parts: List<SingleLineStringPart> by lazy {
    children.filterIsInstance<SingleLineStringPart>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitStringLiteral(this)
  }
}

class SingleLineStringPartImpl(
  override val project: Project,
  override val parent: Node,
  ctx: SingleLineStringPartContext,
) : AbstractNode(project, parent, ctx), SingleLineStringPart {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSingleLineStringPart(this)
  }
}

class PklMultiLineStringLiteralImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: MultiLineStringLiteralContext,
) : AbstractNode(project, parent, ctx), PklMultiLineStringLiteral {
  override val parts: List<MultiLineStringPart> by lazy {
    children.filterIsInstance<MultiLineStringPart>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMlStringLiteral(this)
  }
}

class MultiLineStringPartImpl(
  override val project: Project,
  override val parent: Node,
  ctx: MultiLineStringPartContext,
) : AbstractNode(project, parent, ctx), MultiLineStringPart {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMlStringPart(this)
  }
}

class PklNewExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: NewExprContext,
) : AbstractNode(project, parent, ctx), PklNewExpr {
  override val type: PklType? by lazy { children.firstInstanceOf<PklType>() }
  override val objectBody: PklObjectBody? by lazy { children.firstInstanceOf<PklObjectBody>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNewExpr(this)
  }
}

class PklAmendExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: AmendExprContext,
) : AbstractNode(project, parent, ctx), PklAmendExpr {
  override val parentExpr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }
  override val objectBody: PklObjectBody by lazy { children.firstInstanceOf<PklObjectBody>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitAmendExpr(this)
  }
}

class PklSuperAccessExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: SuperAccessExprContext,
) : AbstractNode(project, parent, ctx), PklSuperAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val memberNameText: String by lazy { identifier!!.text }
  override val isNullSafeAccess: Boolean = false
  override val argumentList: PklArgumentList? by lazy {
    children.firstInstanceOf<PklArgumentList>()
  }

  override fun resolve(): Node? {
    val base = project.pklBaseModule
    val visitor = ResolveVisitors.firstElementNamed(memberNameText, base)
    return resolve(base, null, mapOf(), visitor)
  }

  override fun <R> resolve(
    base: PklBaseModule,
    receiverType: Type?,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>,
  ): R {
    // TODO: Pkl doesn't currently enforce that `super.foo`
    // has the same type as `this.foo` if `super.foo` is defined in a superclass.
    // In particular, covariant property types are used in the wild.
    val thisType = receiverType ?: computeThisType(base, bindings)
    return Resolvers.resolveQualifiedAccess(thisType, isPropertyAccess, base, visitor)
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSuperAccessExpr(this)
  }
}

class PklSuperSubscriptExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: SuperSubscriptExprContext,
) : AbstractNode(project, parent, ctx), PklSuperSubscriptExpr {
  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSuperSubscriptExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else "]"
  }
}

class PklQualifiedAccessExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: QualifiedAccessExprContext,
) : AbstractNode(project, parent, ctx), PklQualifiedAccessExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val memberNameText: String by lazy { identifier!!.text }
  override val isNullSafeAccess: Boolean by lazy { ctx.QDOT() != null }
  override val argumentList: PklArgumentList? by lazy {
    children.firstInstanceOf<PklArgumentList>()
  }
  override val receiverExpr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }

  override fun resolve(): Node? {
    val base = project.pklBaseModule
    val visitor = ResolveVisitors.firstElementNamed(memberNameText, base)
    // TODO: check if receiver is `module`
    return resolve(base, null, mapOf(), visitor)
  }

  override fun <R> resolve(
    base: PklBaseModule,
    receiverType: Type?,
    bindings: TypeParameterBindings,
    visitor: ResolveVisitor<R>,
  ): R {
    val myReceiverType: Type = receiverType ?: receiverExpr.computeExprType(base, bindings)
    return Resolvers.resolveQualifiedAccess(myReceiverType, isPropertyAccess, base, visitor)
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitQualifiedAccessExpr(this)
  }
}

class PklSubscriptExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: SubscriptExprContext,
) : AbstractNode(project, parent, ctx), PklSubscriptExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitSubscriptExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else "]"
  }
}

class PklNonNullExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: NonNullExprContext,
) : AbstractNode(project, parent, ctx), PklNonNullExpr {
  override val expr: PklExpr by lazy { children.firstInstanceOf<PklExpr>()!! }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNonNullExpr(this)
  }
}

class PklUnaryMinusExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: UnaryMinusExprContext,
) : AbstractNode(project, parent, ctx), PklUnaryMinusExpr {
  override val expr: PklExpr by lazy { ctx.expr().toNode(project, this) as PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitUnaryMinusExpr(this)
  }
}

class PklLogicalNotExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: LogicalNotExprContext,
) : AbstractNode(project, parent, ctx), PklLogicalNotExpr {
  override val expr: PklExpr by lazy { ctx.expr().toNode(project, this) as PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLogicalNotExpr(this)
  }
}

class PklAdditiveExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: AdditiveExprContext,
) : AbstractNode(project, parent, ctx), PklAdditiveExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitAdditiveExpr(this)
  }
}

class PklMultiplicativeExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: MultiplicativeExprContext,
) : AbstractNode(project, parent, ctx), PklMultiplicativeExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitMultiplicativeExpr(this)
  }
}

class PklComparisonExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ComparisonExprContext,
) : AbstractNode(project, parent, ctx), PklComparisonExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitComparisonExpr(this)
  }
}

class PklEqualityExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: EqualityExprContext,
) : AbstractNode(project, parent, ctx), PklEqualityExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitEqualityExpr(this)
  }
}

class PklExponentiationExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ExponentiationExprContext,
) : AbstractNode(project, parent, ctx), PklExponentiationExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitExponentiationExpr(this)
  }
}

class PklLogicalAndExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: LogicalAndExprContext,
) : AbstractNode(project, parent, ctx), PklLogicalAndExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLogicalAndExpr(this)
  }
}

class PklLogicalOrExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: LogicalOrExprContext,
) : AbstractNode(project, parent, ctx), PklLogicalOrExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLogicalOrExpr(this)
  }
}

class PklNullCoalesceExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: NullCoalesceExprContext,
) : AbstractNode(project, parent, ctx), PklNullCoalesceExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitNullCoalesceExpr(this)
  }
}

class PklTypeTestExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: TypeTestExprContext,
) : AbstractNode(project, parent, ctx), PklTypeTestExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val type: PklType by lazy { children.firstInstanceOf<PklType>()!! }
  override val operator: TypeTestOperator by lazy {
    if (ctx.IS() != null) TypeTestOperator.IS else TypeTestOperator.AS
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypeTestExpr(this)
  }
}

class PklPipeExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: PipeExprContext,
) : AbstractNode(project, parent, ctx), PklPipeExpr {
  override val leftExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val rightExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val operator: Terminal by lazy { terminals[0] }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitPipeExpr(this)
  }
}

class PklIfExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: IfExprContext,
) : AbstractNode(project, parent, ctx), PklIfExpr {
  override val conditionExpr: PklExpr by lazy { ctx.c.toNode(project, this) as PklExpr }
  override val thenExpr: PklExpr by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val elseExpr: PklExpr by lazy { ctx.r.toNode(project, this) as PklExpr }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitIfExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklLetExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: LetExprContext,
) : AbstractNode(project, parent, ctx), PklLetExpr {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val varExpr: PklExpr? by lazy { ctx.l.toNode(project, this) as PklExpr }
  override val bodyExpr: PklExpr? by lazy { ctx.r.toNode(project, this) as PklExpr }
  override val parameter: PklParameter? by lazy { children.firstInstanceOf<PklParameter>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitLetExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklFunctionLiteralExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: FunctionLiteralContext,
) : AbstractNode(project, parent, ctx), PklFunctionLiteralExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }
  override val parameterList: PklParameterList by lazy {
    children.firstInstanceOf<PklParameterList>()!!
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitFunctionLiteral(this)
  }
}

class PklParenthesizedExprImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ParenthesizedExprContext,
) : AbstractNode(project, parent, ctx), PklParenthesizedExpr {
  override val expr: PklExpr? by lazy { children.firstInstanceOf<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitParenthesizedExpr(this)
  }

  override fun checkClosingDelimiter(): String? {
    return if (ctx.err != null) null else ")"
  }
}

class PklTypedIdentifierImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: TypedIdentifierContext,
) : AbstractNode(project, parent, ctx), PklTypedIdentifier {
  override val identifier: Terminal? by lazy { terminals.find { it.type == TokenType.Identifier } }
  override val typeAnnotation: PklTypeAnnotation? by lazy {
    children.firstInstanceOf<PklTypeAnnotation>()
  }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitTypedIdentifier(this)
  }
}

class PklParameterImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ParameterContext,
) : AbstractNode(project, parent, ctx), PklParameter {
  override val isUnderscore: Boolean by lazy { ctx.UNDERSCORE() != null }
  override val typedIdentifier: PklTypedIdentifier? by lazy {
    children.firstInstanceOf<PklTypedIdentifier>()
  }
  override val type: PklType? by lazy { typedIdentifier?.typeAnnotation?.type }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitParameter(this)
  }
}

class PklArgumentListImpl(
  override val project: Project,
  override val parent: Node,
  override val ctx: ArgumentListContext,
) : AbstractNode(project, parent, ctx), PklArgumentList {
  override val elements: List<PklExpr> by lazy { children.filterIsInstance<PklExpr>() }

  override fun <R> accept(visitor: PklVisitor<R>): R? {
    return visitor.visitArgumentList(this)
  }

  override fun checkClosingDelimiter(): String? {
    if (ctx.expr().isNotEmpty() && ctx.errs.size != ctx.expr().size - 1) {
      return ","
    }
    return if (ctx.err != null) null else ")"
  }
}