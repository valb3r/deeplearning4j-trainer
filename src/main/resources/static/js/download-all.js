function downloadAll(processId, dataSetSize) {
    let link = document.createElement('a');
    link.setAttribute('download', null);
    link.style.display = 'none';

    document.body.appendChild(link);

    for (let i = 0; i < dataSetSize; i++) {
        link.setAttribute('href', `/user/processes/${processId}/download-dataset?dataSetPos=${i}`);
        link.click();
    }

    document.body.removeChild(link);
}