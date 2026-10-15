package com.example.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

data class ParsedAutofillStructure(
    val packageName: String,
    val webDomain: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val isPasswordFieldPresent: Boolean,
    val isUsernameFieldPresent: Boolean
)

object AutofillParser {

    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        val packageName = structure.activityComponent?.packageName ?: ""
        var extractedWebDomain: String? = null
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null

        val nodeCount = structure.windowNodeCount
        for (i in 0 until nodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val root = windowNode.rootViewNode ?: continue
            val result = traverseNode(root)

            if (result.webDomain != null && extractedWebDomain == null) {
                extractedWebDomain = result.webDomain
            }
            if (result.usernameId != null && usernameId == null) {
                usernameId = result.usernameId
            }
            if (result.passwordId != null && passwordId == null) {
                passwordId = result.passwordId
            }
        }

        return ParsedAutofillStructure(
            packageName = packageName,
            webDomain = extractedWebDomain,
            usernameId = usernameId,
            passwordId = passwordId,
            isPasswordFieldPresent = (passwordId != null),
            isUsernameFieldPresent = (usernameId != null)
        )
    }

    private data class NodeTraversalResult(
        val usernameId: AutofillId? = null,
        val passwordId: AutofillId? = null,
        val webDomain: String? = null
    )

    private fun traverseNode(node: AssistStructure.ViewNode): NodeTraversalResult {
        var foundUsername: AutofillId? = null
        var foundPassword: AutofillId? = null
        var foundDomain: String? = node.webDomain

        // 1. Check autofillHints first
        val hints = node.autofillHints
        if (hints != null) {
            for (hint in hints) {
                when (hint.lowercase()) {
                    View.AUTOFILL_HINT_PASSWORD -> foundPassword = node.autofillId
                    View.AUTOFILL_HINT_USERNAME,
                    View.AUTOFILL_HINT_EMAIL_ADDRESS -> foundUsername = node.autofillId
                }
            }
        }

        // 2. Fall back to inputType heuristics
        val inputType = node.inputType
        if (foundPassword == null && isPasswordInputType(inputType)) {
            foundPassword = node.autofillId
        }
        if (foundUsername == null && isUsernameInputType(inputType)) {
            foundUsername = node.autofillId
        }

        // 3. Fall back to hint text, contentDescription, or idEntry
        val hintText = node.hint?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val idEntry = node.idEntry?.lowercase() ?: ""

        if (foundPassword == null) {
            if (hintText.contains("password") ||
                hintText.contains("passcode") ||
                contentDesc.contains("password") ||
                idEntry.contains("password") ||
                idEntry.contains("pwd")
            ) {
                foundPassword = node.autofillId
            }
        }

        if (foundUsername == null) {
            if (hintText.contains("username") ||
                hintText.contains("email") ||
                hintText.contains("user id") ||
                hintText.contains("login") ||
                contentDesc.contains("username") ||
                contentDesc.contains("email") ||
                idEntry.contains("username") ||
                idEntry.contains("email") ||
                idEntry.contains("login_id")
            ) {
                foundUsername = node.autofillId
            }
        }

        // Recursively traverse child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) ?: continue
            val childResult = traverseNode(child)

            if (foundDomain == null && childResult.webDomain != null) {
                foundDomain = childResult.webDomain
            }
            if (foundUsername == null && childResult.usernameId != null) {
                foundUsername = childResult.usernameId
            }
            if (foundPassword == null && childResult.passwordId != null) {
                foundPassword = childResult.passwordId
            }
        }

        return NodeTraversalResult(
            usernameId = foundUsername,
            passwordId = foundPassword,
            webDomain = foundDomain
        )
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun isUsernameInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    /**
     * Extracts a clean, registrable domain from a raw URL or domain string.
     * E.g. "https://m.facebook.com/login" -> "facebook.com"
     */
    fun extractRegistrableDomain(raw: String): String {
        var clean = raw.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
            .substringBefore('?')

        val parts = clean.split('.')
        return if (parts.size >= 2) {
            // Keep last two segments (e.g. facebook.com, github.com)
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            clean
        }
    }
}
