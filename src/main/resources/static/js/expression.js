var processVariables = undefined
var idCounter = 0;
var chart = undefined;

function autocompleteSavedExpressions(listElementId) {
    const list = document.getElementById(listElementId);

    list.replaceChildren();
    readExpressionConfig().forEach((config) => {
        let option = document.createElement('option');
        option.value = config.join("; ");
        list.appendChild(option);
    })
}

function savedExpressionsSelected(inputId, listId, processId, chartsListId, dataPointIdxElemId, useLatest) {
    const val = document.getElementById(inputId).value;
    const opts = document.getElementById(listId).childNodes;
    const chartList = document.getElementById(chartsListId);

    for (let i = 0; i < opts.length; i++) {
        if (opts[i].value === val) {
            chartList.replaceChildren();
            val.split("; ").forEach((exprDef) => {
                addExpressionToList(exprDef, processId, chartsListId, dataPointIdxElemId, useLatest)
            });
            updateExpression(processId, chartsListId, dataPointIdxElemId, useLatest)
            break;
        }
    }
}

function addExpressionToList(exprVal, processId, chartsListId, dataPointIdxElemId, useLatest) {
    let option = document.createElement('li');
    let list = document.getElementById(chartsListId);
    option.exprId = `${exprVal}`;
    option.id = `option-${idCounter++}`;
    let span = document.createElement('span');
    span.innerHTML = `${option.exprId} <a href="#" onclick="removeExpression('${processId}', '${chartsListId}', '${option.id}', '${dataPointIdxElemId}', '${useLatest}')">Delete</a>`
    option.appendChild(span);
    list.appendChild(option);
}

function addExpression(processId, exprListId, inputExprId, dataPointIdxElemId, useLatest) {
    let inputExprVal = document.getElementById(inputExprId).value
    addExpressionToList(inputExprVal, processId, exprListId, dataPointIdxElemId, useLatest);
    updateExpression(processId, exprListId, dataPointIdxElemId, useLatest)
}

function removeExpression(processId, exprListId, optionId, dataPointIdxElemId, useLatest) {
    document.getElementById(exprListId).removeChild(document.getElementById(optionId))
    updateExpression(processId, exprListId, dataPointIdxElemId, useLatest)
}

function updateExpressionPointIdx(processId, chartsListId, dataPointIdxElemId, useLatest, updateType) {
    updatedValue = parseInt(document.getElementById(dataPointIdxElemId).value)
    const dsSize = parseInt(dataSize)
    switch (updateType) {
        case 'next':
            if (updatedValue + 1 > dsSize) {
                return
            }
            updatedValue++
            break;
        case 'prev':
            if (updatedValue - 1 < 1) {
                return
            }
            updatedValue--;
            break;
        case 'random':
            updatedValue = Math.floor(Math.random() * (dsSize + 1))
            break;
        case 'blur':
            break;
        default:
            throw Error(`Unknown updateType ${updateType}`)
    }
    document.getElementById(dataPointIdxElemId).value = updatedValue
    updateExpression(processId, chartsListId, dataPointIdxElemId, useLatest)
}

function extractExpressions(list) {
    const pairs = []
    list.childNodes.forEach((node) => {
        if (node.tagName === "li" || node.tagName === "LI") {
            pairs.push(node.exprId)
        }
    });
    return pairs;
}

function updateExpression(processId, exprListId, dataPointIdxElemId, useLatest) {
    const list = document.getElementById(exprListId);
    const pairs = extractExpressions(list);
    fetchAndEvaluate(processId, pairs, document.getElementById(dataPointIdxElemId).value, useLatest)
}

function readExpressionConfig() {
    const result = JSON.parse(window.localStorage.getItem("__expression-config"))
    if (!result) {
        return []
    }
    return result
}

function rememberExpressionConfig(chartsListId) {
    const list = document.getElementById(chartsListId);
    const pairs = extractExpressions(list);
    const cfg = readExpressionConfig()
    if (cfg.length === 3) {
        cfg.pop()
    }
    cfg.unshift(pairs)
    window.localStorage.setItem("__expression-config", JSON.stringify(cfg))
}

function fetchAndEvaluate(processId, exprPairs, trainingEntryIdx, useLatest) {
    document.getElementById("loader").classList.remove("hide-loader")

    let isLatest = document.getElementById(useLatest).checked
    if (0 === exprPairs.length) {
        showExprResult([])
        return
    }

    fetch(`/api/${processId}/evaluate-expressions?${new URLSearchParams({expressions: exprPairs, trainingEntryIdx: trainingEntryIdx, latest: isLatest})}`)
        .then((response) => response.json())
        .then((data) => showExprResult(data))
        .catch((err) => console.error(`Failed fetching: ${err}`));
}

function showExprResult(data) { // data is Map<String, FloatArray>
    document.getElementById("loader").classList.add("hide-loader")
    document.getElementById("expr").value = `${JSON.stringify(data, null, 4)}`
}
