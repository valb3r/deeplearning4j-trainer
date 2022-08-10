package com.valb3r.deeeplearning4j_trainer.spring.flowable.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class ModelSpec(
    val layers: List<LayerDef>
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SirenLayerDef::class, name = "siren"),
    JsonSubTypes.Type(value = DenseLayerDef::class, name = "dense"),
    JsonSubTypes.Type(value = DiagonalLayerDef::class, name = "diagonal-layer"),
    JsonSubTypes.Type(value = ConcatLayerDef::class, name = "concat"),
    JsonSubTypes.Type(value = ReferenceLayerDef::class, name = "reference-layer"),
    JsonSubTypes.Type(value = PadLayerDef::class, name = "pad"),
    JsonSubTypes.Type(value = SumLayerDef::class, name = "sum"),
)
open class LayerDef(
    val name: String,
    val outputVarName: String?,
    val inputVariables: List<String>?,
)

class SirenLayerDef(
    @JsonProperty(required = true) val `in`: Int,
    @JsonProperty(required = true) val repeatFactor: Int,
    name: String = "siren-layer",
    outputVarName: String?,
    inputVariables: List<String>?
): LayerDef(name, outputVarName, inputVariables)

class ReferenceLayerDef(
    val reference: String,
    name: String = "reference-layer",
    outputVarName: String?,
    inputVariables: List<String>?
): LayerDef(name, outputVarName, inputVariables)

class PadLayerDef(
    @JsonProperty(required = true) val dimensions: Array<LongArray>,
    @JsonProperty(required = true) val value: Double,
    name: String = "pad-layer",
    outputVarName: String?,
    inputVariables: List<String>?
): LayerDef(name, outputVarName, inputVariables)

class SumLayerDef(
    val value: Double?,
    name: String = "sum-layer",
    outputVarName: String?,
    inputVariables: List<String>?
): LayerDef(name, outputVarName, inputVariables)

open class BaseDenseLayer(
    @JsonProperty(required = true) val `in`: Int,
    @JsonProperty(required = true) val `out`: Int,
    val activation: Activation?,
    name: String,
    outputVarName: String?,
    inputVariables: List<String>?,
): LayerDef(name, outputVarName, inputVariables)

open class DenseLayerDef(
    val initScheme: InitScheme?,
    @JsonProperty(required = true) `in`: Int,
    @JsonProperty(required = true) out: Int,
    activation: Activation?,
    name: String = "dense-layer",
    outputVarName: String?,
    inputVariables: List<String>?
) : BaseDenseLayer(`in`, out, activation, name, outputVarName, inputVariables)

class DiagonalLayerDef(
    initScheme: InitScheme?,
    @JsonProperty(required = true) `in`: Int,
    @JsonProperty(required = true) out: Int,
    activation: Activation?,
    name: String = "diagonal-layer",
    outputVarName: String?,
    inputVariables: List<String>?
): DenseLayerDef(initScheme, `in`, out, activation, name, outputVarName, inputVariables)

class ConcatLayerDef(
    @JsonProperty(required = true) val concatDim: Int,
    name: String = "concat-layer",
    outputVarName: String?,
    inputVariables: List<String>?
): LayerDef(name, outputVarName, inputVariables)

data class Activation(
    val type: String,
    val params: Array<Double>? = null
)

data class InitScheme(
    val type: String,
    val params: Array<Double>? = null
)