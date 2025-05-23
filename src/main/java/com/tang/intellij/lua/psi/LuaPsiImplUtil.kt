/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UNUSED_PARAMETER")

package com.tang.intellij.lua.psi

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.LuaDocAccessModifier
import com.tang.intellij.lua.comment.psi.LuaDocTagVararg
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.lang.type.LuaString
import com.tang.intellij.lua.psi.impl.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.LuaClassMemberStub
import com.tang.intellij.lua.stubs.LuaFuncBodyOwnerStub
import com.tang.intellij.lua.ty.*
import java.util.*
import javax.swing.Icon

fun setName(owner: PsiNameIdentifierOwner, name: String): PsiElement {
    val oldId = owner.nameIdentifier
    if (oldId != null) {
        val newId = LuaElementFactory.createIdentifier(owner.project, name)
        oldId.replace(newId)
        return newId
    }
    return owner
}

fun getNameIdentifier(nameDef: LuaNameDef): PsiElement {
    return nameDef.firstChild
}

/**
 * LuaNameDef 只可能在本文件中搜
 * @param nameDef def
 * *
 * @return SearchScope
 */
fun getUseScope(nameDef: LuaNameDef): SearchScope {
    return GlobalSearchScope.fileScope(nameDef.containingFile)
}

fun getReferences(element: LuaPsiElement): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
}

/**
 * 寻找 Comment
 * @param declaration owner
 * *
 * @return LuaComment
 */
fun getComment(declaration: LuaCommentOwner): LuaComment? {
    return LuaCommentUtil.findComment(declaration)
}

fun getNameIdentifier(classMethodDef: LuaClassMethodDef): PsiElement? {
    return classMethodDef.classMethodName.id
}

fun getName(classMethodDef: LuaClassMethodDef): String? {
    val stub = classMethodDef.stub
    if (stub != null)
        return stub.name
    return getName(classMethodDef as PsiNameIdentifierOwner)
}

fun isStatic(classMethodDef: LuaClassMethodDef): Boolean {
    val stub = classMethodDef.stub
    if (stub != null)
        return stub.isStatic

    return classMethodDef.classMethodName.dot != null
}

fun getPresentation(classMethodDef: LuaClassMethodDef): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            val type = classMethodDef.guessClassType(SearchContext.get(classMethodDef.project))
            if (type != null) {
                val c = if (classMethodDef.isStatic) "." else ":"
                return type.displayName + c + classMethodDef.name + classMethodDef.paramSignature
            }
            return classMethodDef.name!! + classMethodDef.paramSignature
        }

        override fun getLocationString(): String {
            return classMethodDef.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_METHOD
        }
    }
}

/**
 * 寻找对应的类
 * @param classMethodDef def
 * *
 * @return LuaType
 */
fun guessParentType(classMethodDef: LuaClassMethodDef, context: SearchContext): ITy {
    /*val stub = classMethodDef.stub
    var type: ITy = Ty.UNKNOWN
    if (stub != null) {
        stub.classes.forEach {
           type = type.union(it)
        }
    } else {
        val expr = classMethodDef.classMethodName.expr
        val ty = expr.guessType(ctx)
        val perfect = TyUnion.getPerfectClass(ty)
        if (perfect is ITyClass)
            type = perfect
    }*/

    val expr = classMethodDef.classMethodName.expr
    val ty = expr.guessType(context)
    return TyUnion.getPerfectClass(ty) ?: Ty.UNKNOWN
}

fun getNameIdentifier(funcDef: LuaFuncDef): PsiElement? {
    return funcDef.id
}

fun getName(funcDef: LuaFuncDef): String? {
    val stub = funcDef.stub
    if (stub != null)
        return stub.name
    return getName(funcDef as PsiNameIdentifierOwner)
}

fun getPresentation(funcDef: LuaFuncDef): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            val name = funcDef.name
            return if (name == null)
                null
            else name + funcDef.paramSignature
        }

        override fun getLocationString(): String {
            return funcDef.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return AllIcons.Nodes.Function
        }
    }
}

fun guessParentType(funcDef: LuaFuncDef, searchContext: SearchContext): ITyClass {
    return TyClass.G
}

/**
 * 猜出前面的类型
 * @param callExpr call expr
 * *
 * @return LuaType
 */
fun guessParentType(callExpr: LuaCallExpr, context: SearchContext): ITy {
    return callExpr.expr.guessType(context)
}

fun getStringValue(valueExpr: PsiElement): String {
    if (valueExpr is LuaLiteralExprImpl)
    {
        return valueExpr.stringValue
    }
    else
    {
        if (valueExpr is LuaNameExpr)
        {
            val declaration = resolve(valueExpr as LuaNameExpr, SearchContext.get(valueExpr.project))
            if (declaration is LuaNameExprImpl)
            {
                return declaration.text
            }
            else if(declaration is LuaNameDefImpl)
            {
                val exp = declaration?.parent?.parent?.lastChild?.lastChild
                if (exp != null)
                {
                    return getStringValue(exp)
                }
            }
        }
        else if (valueExpr is LuaIndexExpr)
        {
            val declaration = resolve(valueExpr as LuaIndexExpr, SearchContext.get(valueExpr.project))
            if (declaration is LuaTableFieldImpl)
            {
                val strExp = declaration?.lastChild
                if (strExp != null)
                {
                    return getStringValue(strExp)
                }
            }
            else if (declaration is LuaIndexExprImpl)
            {
                val exp = declaration?.parent?.parent?.lastChild?.lastChild
                if (exp != null)
                {
                    return getStringValue(exp)
                }
            }
        }
    }

    return "";
}

fun getParamStringValue(valueExpr: PsiElement): String {
    if (valueExpr is LuaNameExpr)
    {
        return valueExpr.text;
    }
    else if(valueExpr is LuaIndexExpr)
    {
        return valueExpr.lastChild.text;
    }
    return "";
}

fun getParamAllStringValue(valueExpr: PsiElement): String {
    if (valueExpr is LuaNameExpr)
    {
        return valueExpr.text;
    }
    else if(valueExpr is LuaIndexExpr)
    {
        return valueExpr.text;
    }
    return "";
}

/**
 * 获取第n个字符串参数
 * @param callExpr callExpr
 * *
 * @return String PsiElement
 */
fun getStringArgByIndex(callExpr: LuaCallExpr, index: Int): PsiElement? {
    val args = callExpr.args
    var path: PsiElement? = null

    when (args) {
        is LuaSingleArg -> {
            val expr = args.expr
            if (expr is LuaLiteralExpr && index == 0) path = expr
        }
        is LuaListArgs -> args.exprList.let { list ->
            if (list.isNotEmpty() && list.size > index) {
                if (list[index] is LuaLiteralExpr) {
                    val valueExpr = list[index] as LuaLiteralExpr
                    if (valueExpr.kind == LuaLiteralKind.String)
                        path = valueExpr
                }
                else {
                    val context = SearchContext.get(callExpr.project)
                    if (list[index].guessType((context)).displayName == "string")
                    {
                        path = list[index]
                    }
                }
            }
        }
    }
    return path
}

/**
 * 获取第n个参数的名字
 * @param callExpr callExpr
 * *
 * @return String PsiElement
 */
fun getParamNameByIndex(callExpr: LuaCallExpr, index: Int): PsiElement? {
    val args = callExpr.args
    var path: PsiElement? = null

    when (args) {
        is LuaSingleArg -> {
            val expr = args.expr
            if (expr is LuaNameExpr && index == 0) path = expr
            if (expr is LuaIndexExpr && index == 0) path = expr
        }
        is LuaListArgs -> args.exprList.let { list ->
            if (list.isNotEmpty() && list.size > index) {
                if (list[index] is LuaNameExpr) {
                    path = list[index]
                }
                else if (list[index] is LuaIndexExpr) {
                    path = list[index]
                }
            }
        }
    }
    return path
}

fun isMethodDotCall(callExpr: LuaCallExpr): Boolean {
    val expr = callExpr.expr
    if (expr is LuaNameExpr)
        return true
    return expr is LuaIndexExpr && expr.colon == null
}

fun isMethodColonCall(callExpr: LuaCallExpr): Boolean {
    val expr = callExpr.expr
    return expr is LuaIndexExpr && expr.colon != null
}

fun isFunctionCall(callExpr: LuaCallExpr): Boolean {
    return callExpr.expr is LuaNameExpr
}

fun LuaExprList.at(index: Int): LuaExpr? {
    val exprList = exprStubList
    return exprList.getOrNull(index) ?: exprList.lastOrNull()
}

fun guessTypeAt(list: LuaExprList, context: SearchContext): ITy {
    val expr = list.at(context.index)
    if (expr != null) {
        //local function getValues12() return 1, 2 end
        //local function getValues34() return 3, 4 end
        //local a, b = getValues12() -- a = 1, b = 2
        //local a, b, c = getValues12(), 3, 4 --a = 1, b = 3, c =  4
        //local a, b, c = getValues12(), getValue34() --a = 1, b = 3, c =  4
        var index = context.index
        val exprList = list.exprStubList
        if (exprList.size > 1) {
            val nameSize = context.index + 1
            index = if (nameSize > exprList.size) {
                val valueSize = exprList.size
                nameSize - valueSize
            } else 0
        }
        return context.withIndex(index) { expr.guessType(context) }
    }
    return Ty.UNKNOWN
}

fun getStringValue(typeName: LuaLiteralExpr): String {
    return typeName.stringValue;
}

fun getNameExprStringValue(valueExpr: PsiElement): String {
    val tree = LuaDeclarationTree.get(valueExpr.containingFile)
    val declaration = tree.find(valueExpr as LuaExpr)?.firstDeclaration?.psi
    val exp = declaration?.parent?.parent
    if (exp != null)
    {
        val strExp = exp.lastChild.lastChild
        if(strExp is LuaLiteralExprImpl)
        {
            val str = strExp.text
            return str.substring(1, str.length - 1)
        }
        else
        {
            return getNameExprStringValue(strExp)
        }

    }
    return "";
}

fun newType(typeName: String, ty: ITy, sourceStr: String, targetStr: String): ITy {
    if (ty is TyArray) {
        val t = typeName.substringBefore('[').trim()
        val ty = TyLazyClass(t)
        return TyArray(ty);
    }
    else if(ty is TySerializedGeneric){
        val list = mutableListOf<ITy>();
        ty.params.forEach {
            var name = it.displayName
            if(name.contains(sourceStr))
            {
                name = name.replace(sourceStr, targetStr)
            }
            list.add(newType(name, it, sourceStr, targetStr))
        }
        return TySerializedGeneric(list.toTypedArray(), ty.base)
    }
    else {
        return TyLazyClass(typeName);
    }
}

fun guessParentType(indexExpr: LuaIndexExpr, context: SearchContext): ITy {
    val expr = PsiTreeUtil.getStubChildOfType(indexExpr, LuaExpr::class.java)
    return expr?.guessType(context) ?: Ty.UNKNOWN
}

fun getNameIdentifier(indexExpr: LuaIndexExpr): PsiElement? {
    val id = indexExpr.id
    if (id != null)
        return id
    return indexExpr.idExpr
}

fun getPresentation(indexExpr: LuaIndexExpr): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return indexExpr.name
        }

        override fun getLocationString(): String {
            return indexExpr.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_FIELD
        }
    }
}

/**
 * xx['id']
 */
fun getIdExpr(indexExpr: LuaIndexExpr): LuaLiteralExpr? {
    val bracket = indexExpr.lbrack
    if (bracket != null) {
        val nextLeaf = PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpr::class.java)
        if (nextLeaf is LuaLiteralExpr && nextLeaf.kind == LuaLiteralKind.String)
            return nextLeaf
    }
    return null
}

fun getName(indexExpr: LuaIndexExpr): String? {
    val stub = indexExpr.stub
    if (stub != null)
        return stub.name

    // var.name
    val id = indexExpr.id
    if (id != null)
        return id.text

    // var['name']
    val idExpr = indexExpr.idExpr
    if (idExpr != null)
        return LuaString.getContent(idExpr.text).value

    return null
}

fun setName(indexExpr: LuaIndexExpr, name: String): PsiElement {
    if (indexExpr.id != null)
        return setName(indexExpr as PsiNameIdentifierOwner, name)
    val idExpr = indexExpr.idExpr
    if (idExpr != null) {
        val text = idExpr.text
        val content = LuaString.getContent(text)
        val newText = text.substring(0, content.start) + name + text.substring(content.end)
        val newId = LuaElementFactory.createLiteral(indexExpr.project, newText)
        return idExpr.replace(newId)
    }
    return indexExpr
}

fun findField(table: LuaTableExpr, fieldName: String): LuaTableField? {
    return table.tableFieldList.firstOrNull { fieldName == it.name }
}

fun getParamNameDefList(funcBodyOwner: LuaFuncBodyOwner): List<LuaParamNameDef> {
    val funcBody = funcBodyOwner.funcBody
    return funcBody?.paramNameDefList ?: emptyList()
}

fun getParamNameDefList(forAStat: LuaForAStat): List<LuaParamNameDef> {
    val list = ArrayList<LuaParamNameDef>()
    list.add(forAStat.paramNameDef)
    return list
}

fun guessReturnType(owner: LuaFuncBodyOwner, searchContext: SearchContext): ITy {
    return inferReturnTy(owner, searchContext)
}

fun getVarargTy(owner: LuaFuncBodyOwner): ITy? {
    if (owner is StubBasedPsiElementBase<*>) {
        val stub = owner.stub
        if (stub is LuaFuncBodyOwnerStub<*>) {
            return stub.varargTy
        }
    }
    owner.funcBody?.ellipsis?.let {
        var ret: ITy? = null
        if (owner is LuaCommentOwner) {
            val varargDef = owner.comment?.findTag(LuaDocTagVararg::class.java)
            ret = varargDef?.type
        }
        return ret ?: Ty.UNKNOWN
    }

    return null
}

fun getParams(owner: LuaFuncBodyOwner): Array<LuaParamInfo> {
    if (owner is StubBasedPsiElementBase<*>) {
        val stub = owner.stub
        if (stub is LuaFuncBodyOwnerStub<*>) {
            return stub.params
        }
    }
    return getParamsInner(owner)
}

private fun getParamsInner(funcBodyOwner: LuaFuncBodyOwner): Array<LuaParamInfo> {
    var comment: LuaComment? = null
    if (funcBodyOwner is LuaCommentOwner) {
        comment = LuaCommentUtil.findComment(funcBodyOwner)
    } else {
        val commentOwner = PsiTreeUtil.getParentOfType(funcBodyOwner, LuaCommentOwner::class.java)
        if (commentOwner != null)
            comment = LuaCommentUtil.findComment(commentOwner)
    }

    val paramNameList = funcBodyOwner.paramNameDefList
    if (paramNameList != null) {
        val list = mutableListOf<LuaParamInfo>()
        for (i in paramNameList.indices) {
            val paramInfo = LuaParamInfo()
            val paramName = paramNameList[i].text
            paramInfo.name = paramName
            // param types
            if (comment != null) {
                val paramDef = comment.getParamDef(paramName)
                if (paramDef != null) {
                    paramInfo.ty = paramDef.type
                }
            }
            list.add(paramInfo)
        }
        return list.toTypedArray()
    }
    return emptyArray()
}

fun getParamSignature(funcBodyOwner: LuaFuncBodyOwner): String {
    val params = funcBodyOwner.params
    val list = arrayOfNulls<String>(params.size)
    for (i in params.indices) {
        val lpi = params[i]
        list[i] = lpi.name
    }
    return "(" + list.joinToString(", ") + ")"
}

fun getName(localFuncDef: LuaLocalFuncDef): String? {
    val stub = localFuncDef.stub
    if (stub != null)
        return stub.name
    return getName(localFuncDef as PsiNameIdentifierOwner)
}

fun getNameIdentifier(localFuncDef: LuaLocalFuncDef): PsiElement? {
    return localFuncDef.id
}

fun getUseScope(localFuncDef: LuaLocalFuncDef): SearchScope {
    return GlobalSearchScope.fileScope(localFuncDef.containingFile)
}

fun getName(nameDef: LuaNameDef): String {
    val stub = nameDef.stub
    if (stub != null)
        return stub.name
    return nameDef.id.text
}

fun getName(identifierOwner: PsiNameIdentifierOwner): String? {
    val id = identifierOwner.nameIdentifier
    return id?.text
}

fun getTextOffset(localFuncDef: PsiNameIdentifierOwner): Int {
    val id = localFuncDef.nameIdentifier
    if (id != null) return id.textOffset
    return localFuncDef.node.startOffset
}

fun getNameIdentifier(tableField: LuaTableField): PsiElement? {
    val id = tableField.id
    if (id != null)
        return id
    return tableField.idExpr
}

fun guessParentType(tableField: LuaTableField, context: SearchContext): ITy {
    val tbl = PsiTreeUtil.getParentOfType(tableField, LuaTableExpr::class.java)!!
    return tbl.guessType(context)
}

fun guessType(tableField: LuaTableField, context: SearchContext): ITy {
    val stub = tableField.stub
    //from comment
    val docTy = if (stub != null) stub.docTy else tableField.comment?.docTy
    if (docTy != null)
        return docTy

    //guess from value
    val valueExpr = PsiTreeUtil.getStubChildOfType(tableField, LuaExpr::class.java)
    if (valueExpr != null) {
        return valueExpr.guessType(context)
    }
    return Ty.UNKNOWN
}

fun getName(tableField: LuaTableField): String? {
    val stub = tableField.stub
    if (stub != null)
        return stub.name
    val id = tableField.id
    if (id != null)
        return id.text

    val idExpr = tableField.idExpr
    if (idExpr is LuaLiteralExpr && idExpr.kind == LuaLiteralKind.String)
        return LuaString.getContent(idExpr.text).value
    return null
}

fun getFieldName(tableField: LuaTableField): String? {
    return getName(tableField)
}

fun getPresentation(tableField: LuaTableField): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String? {
            return tableField.name
        }

        override fun getLocationString(): String {
            return tableField.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_FIELD
        }
    }
}

/**
 * xx['id']
 */
fun getIdExpr(tableField: LuaTableField): LuaExpr? {
    val bracket = tableField.lbrack
    if (bracket != null) {
        return PsiTreeUtil.getNextSiblingOfType(bracket, LuaExpr::class.java)
    }
    return null
}

fun toString(stubElement: StubBasedPsiElement<out StubElement<*>>): String {
    return "STUB:[" + stubElement.javaClass.simpleName + "]"
}

fun getPresentation(nameExpr: LuaNameExpr): ItemPresentation {
    return object : ItemPresentation {
        override fun getPresentableText(): String {
            return nameExpr.name
        }

        override fun getLocationString(): String {
            return nameExpr.containingFile.name
        }

        override fun getIcon(b: Boolean): Icon? {
            return LuaIcons.CLASS_FIELD
        }
    }
}

fun getNameIdentifier(ref: LuaNameExpr): PsiElement {
    return ref.id
}

fun getName(nameExpr: LuaNameExpr): String {
    val stub = nameExpr.stub
    if (stub != null)
        return stub.name
    return nameExpr.text
}

fun guessReturnType(returnStat: LuaReturnStat?, index: Int, context: SearchContext): ITy {
    if (returnStat != null) {
        val returnExpr = returnStat.exprList
        if (returnExpr != null) {
            return context.withIndex(index) {
                if (context.guessTuple())
                    returnExpr.guessType(context)
                else
                    returnExpr.guessTypeAt(context)
            }
        }
    }
    return Ty.UNKNOWN
}

fun getNameIdentifier(label: LuaLabelStat): PsiElement? {
    return label.id
}

fun getVisibility(member: LuaClassMember): Visibility {
    if (member is StubBasedPsiElement<*>) {
        val stub = member.stub
        if (stub is LuaClassMemberStub) {
            return stub.visibility
        }
    }
    if (member is LuaCommentOwner) {
        val comment = member.comment
        comment?.findTag(LuaDocAccessModifier::class.java)?.let {
            return Visibility.get(it.text)
        }
    }
    return Visibility.PUBLIC
}

fun getVisibility(classMethodDef: LuaClassMethodDef): Visibility {
    val stub = classMethodDef.stub
    if (stub != null) {

    }
    return getVisibility(classMethodDef as LuaClassMember)
}

fun getWorth(classMember: LuaClassMember): Int {
    return when (classMember) {
        is LuaTableField -> LuaClassMember.WORTH_TABLE_FIELD
        is LuaClassMethodDef,
        is LuaFuncDef -> LuaClassMember.WORTH_METHOD_DEF
        else -> LuaClassMember.WORTH_ASSIGN
    }
}

fun getExpr(exprStat: LuaExprStat): LuaExpr {
    return PsiTreeUtil.getStubChildOfType(exprStat, LuaExpr::class.java)!!
}

fun isDeprecated(member: LuaClassMember): Boolean {
    if (member is StubBasedPsiElement<*>) {
        val stub = member.stub
        if (stub is LuaClassMemberStub) {
            return stub.isDeprecated
        }
    }

    if (member is LuaCommentOwner) {
        val comment = member.comment
        if (comment != null)
            return comment.isDeprecated
    }
    return false
}
