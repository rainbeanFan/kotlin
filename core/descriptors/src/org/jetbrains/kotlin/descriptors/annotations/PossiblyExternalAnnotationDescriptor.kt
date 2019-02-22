package org.jetbrains.kotlin.descriptors.annotations

interface PossiblyExternalAnnotationDescriptor : AnnotationDescriptor {
    val isIdeaExternalAnnotation: Boolean
}