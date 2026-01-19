(function(){
  function ensureBatchUI(){
    if (document.getElementById('batchUploadBtn')) return;
    const up = document.querySelector('#fileUploadSection');
    if (up){
      const existingBtn = up.querySelector('.upload-btn');
      if (existingBtn) {
        existingBtn.remove();
      }
      const btn = document.createElement('button');
      btn.id = 'batchUploadBtn';
      btn.className = 'upload-btn';
      btn.style.marginLeft = '8px';
      btn.textContent = '批量上传文件';
      btn.addEventListener('click', () => {
        const modal = document.getElementById('batchModal');
        if (modal) modal.style.display = 'flex'
      });
      up.appendChild(btn);
    }
    if (!document.getElementById('batchModal')) {
      const modal = document.createElement('div');
      modal.id = 'batchModal';
      Object.assign(modal.style, {
        display: 'none',
        position: 'fixed',
        left: '0',
        top: '0',
        width: '100%',
        height: '100%',
        background: 'rgba(0,0,0,.5)',
        zIndex: '1000',
        justifyContent: 'center',
        alignItems: 'center'
      });
      modal.innerHTML = `
        <div style="width:520px; max-width:90%; background:#fff; border-radius:8px; box-shadow:0 4px 20px rgba(0,0,0,0.15); overflow:hidden;">
          <div style="padding:16px 20px; border-bottom:1px solid #e0e0e0; display:flex; justify-content:space-between; align-items:center;">
            <h3 style="margin:0; font-size:16px; color:#333;">批量上传文件</h3>
            <button id="batchCloseBtn" style="background:none; border:none; font-size:24px; cursor:pointer; color:#999; line-height:1;">&times;</button>
          </div>
          <div style="padding:20px;">
            <div style="border:2px dashed #667eea; border-radius:6px; padding:30px; text-align:center; cursor:pointer; transition:all 0.2s;" id="batchDropZone">
              <input id="batchFileInput" type="file" multiple accept=".doc,.docx,.ppt,.pptx,.pdf,.txt,.md,.jpg,.jpeg,.png" style="display:none;" />
              <p style="margin:0 0 10px; color:#667eea; font-size:14px;">点击选择文件或将文件拖拽到此处</p>
              <p style="margin:0; color:#999; font-size:12px;">支持 doc/docx/ppt/pptx/pdf/txt/md/jpg/jpeg/png (最多5个)</p>
            </div>
            <div id="batchFileList" style="max-height:200px; overflow:auto; border:1px solid #e0e0e0; border-radius:4px; padding:10px; margin:10px 0; display:none;"></div>
              <div style="margin-top:15px;">
                <label style="display:block; margin-bottom:8px; font-size:13px; color:#666;">后续操作</label>
                <select id="batchAction" style="width:100%; padding:10px; border:1px solid #e0e0e0; border-radius:4px; font-size:14px;">
                  <option value="TERMS">术语提取(TEMS)</option>
                  <option value="VECTORIZE" selected>向量化存储(VECTORIZE)</option>
                </select>
              </div>
          </div>
          <div style="padding:16px 20px; border-top:1px solid #e0e0e0; text-align:right; background:#fafafa;">
            <button id="batchCancelBtn" style="padding:8px 16px; background:#f5f5f5; color:#666; border:1px solid #e0e0e0; border-radius:4px; cursor:pointer; font-size:13px;">取消</button>
            <button id="batchSubmitBtn" style="margin-left:8px; padding:8px 16px; background:#667eea; color:#fff; border:none; border-radius:4px; cursor:pointer; font-size:13px;">开始上传</button>
          </div>
          <div id="batchProgress" style="padding:0 20px 15px; display:none;">
            <div id="batchProgressText" style="font-size:12px; color:#666; margin-bottom:5px;"></div>
            <div style="height:4px; background:#e0e0e0; border-radius:2px; overflow:hidden;">
              <div id="batchProgressBar" style="width:0%; height:100%; background:#667eea; transition:width 0.3s;"></div>
            </div>
          </div>
          <div id="batchResult" style="display:none; padding:0 20px 20px;">
            <h4 style="margin:0 0 10px; font-size:14px; color:#333;">处理结果</h4>
            <pre id="batchResultContent" style="max-height:200px; overflow:auto; background:#f8f9fa; padding:10px; border:1px solid #e0e0e0; border-radius:4px; font-size:12px; white-space:pre-wrap; word-wrap:break-word;"></pre>
          </div>
        </div>`;
      document.body.appendChild(modal);

      const closeBtn = modal.querySelector('#batchCloseBtn');
      const cancelBtn = modal.querySelector('#batchCancelBtn');
      const submitBtn = modal.querySelector('#batchSubmitBtn');
      const dropZone = modal.querySelector('#batchDropZone');
      const fileInput = modal.querySelector('#batchFileInput');
      const fileList = modal.querySelector('#batchFileList');
      const progressDiv = modal.querySelector('#batchProgress');
      const progressText = modal.querySelector('#batchProgressText');
      const progressBar = modal.querySelector('#batchProgressBar');
      const resultDiv = modal.querySelector('#batchResult');
      const resultContent = modal.querySelector('#batchResultContent');
      const actionSelect = modal.querySelector('#batchAction');

      closeBtn.addEventListener('click', () => {
        modal.style.display = 'none';
        resetModal();
      });

      cancelBtn.addEventListener('click', () => {
        modal.style.display = 'none';
        resetModal();
      });

      modal.addEventListener('click', (e) => {
        if (e.target === modal) {
          modal.style.display = 'none';
          resetModal();
        }
      });

      dropZone.addEventListener('click', () => fileInput.click());

      dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropZone.style.borderColor = '#5568d3';
        dropZone.style.backgroundColor = '#f0f4ff';
      });

      dropZone.addEventListener('dragleave', () => {
        dropZone.style.borderColor = '#667eea';
        dropZone.style.backgroundColor = 'transparent';
      });

      dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.style.borderColor = '#667eea';
        dropZone.style.backgroundColor = 'transparent';
        const files = e.dataTransfer.files;
        handleFileSelect(files);
      });

      fileInput.addEventListener('change', (e) => {
        handleFileSelect(e.target.files);
      });

      function handleFileSelect(files) {
        if (!files || files.length === 0) return;

        const validFiles = Array.from(files).slice(0, 5).filter(file => {
          const ext = file.name.split('.').pop().toLowerCase();
          const validExts = ['doc','docx','ppt','pptx','pdf','txt','md','jpg','jpeg','png'];
          return validExts.includes(ext);
        });

        if (validFiles.length < files.length) {
          alert('已过滤不支持的文件类型，最多支持5个文件');
        }

        if (validFiles.length === 0) {
          alert('没有有效的文件');
          return;
        }

        fileList.innerHTML = '';
        fileList.style.display = 'block';

        const operation = actionSelect.value;

        validFiles.forEach((file, index) => {
          const fileItem = document.createElement('div');
          fileItem.style.cssText = 'display:flex; justify-content:space-between; align-items:center; padding:8px 10px; border-bottom:1px solid #f0f0f0;';
          fileItem.setAttribute('data-file-index', index);

          const fileName = document.createElement('span');
          fileName.style.cssText = 'font-size:13px; color:#333; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:280px;';
          fileName.textContent = file.name;
          fileName.title = file.name;

          const fileActions = document.createElement('div');
          fileActions.style.cssText = 'display:flex; align-items:center; gap:8px;';

          const fileStatus = document.createElement('span');
          fileStatus.style.cssText = 'font-size:11px; color:#999;';
          fileStatus.textContent = formatFileSize(file.size);

          const removeBtn = document.createElement('button');
          removeBtn.style.cssText = 'background:none; border:none; color:#f44336; cursor:pointer; font-size:16px; padding:2px 6px; border-radius:4px;';
          removeBtn.textContent = '×';
          removeBtn.title = '取消上传';
          removeBtn.onclick = () => {
            fileItem.style.opacity = '0.5';
            fileItem.style.textDecoration = 'line-through';
            removeBtn.disabled = true;
            removeBtn.textContent = '已取消';
            fileItem.dataset.cancelled = 'true';
          };

          fileActions.appendChild(fileStatus);
          fileActions.appendChild(removeBtn);
          fileItem.appendChild(fileName);
          fileItem.appendChild(fileActions);
          fileList.appendChild(fileItem);
        });
      }

      function formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
      }

      function resetModal() {
        fileInput.value = '';
        fileList.innerHTML = '';
        fileList.style.display = 'none';
        resultDiv.style.display = 'none';
        progressDiv.style.display = 'none';
        progressBar.style.width = '0%';
        progressText.textContent = '';
      }

      submitBtn.addEventListener('click', async () => {
        const files = fileInput.files;
        if (!files || files.length === 0) {
          alert('请至少选择一个文件');
          return;
        }

        const validFiles = Array.from(files).slice(0, 5).filter(file => {
          const ext = file.name.split('.').pop().toLowerCase();
          const validExts = ['doc','docx','ppt','pptx','pdf','txt','md','jpg','jpeg','png'];
          return validExts.includes(ext);
        });

        if (validFiles.length === 0) {
          alert('没有有效的文件');
          return;
        }

        const fileItems = fileList.querySelectorAll('div[data-file-index]');
        const selectedFiles = [];
        const selectedIndices = [];
        fileItems.forEach((item, idx) => {
          if (item.dataset.cancelled !== 'true') {
            selectedFiles.push(validFiles[idx]);
            selectedIndices.push(idx);
          }
        });

        if (selectedFiles.length === 0) {
          alert('请至少保留一个文件');
          return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = '处理中...';
        progressDiv.style.display = 'block';
        resultDiv.style.display = 'none';

        const operation = actionSelect.value;
        const total = selectedFiles.length;
        const results = [];

        progressText.textContent = `正在上传和处理文件 (0/${total})...`;
        progressBar.style.width = '0%';

        try {
            const formData = new FormData();
            selectedFiles.forEach((file) => {
                formData.append('files', file);
            });
            formData.append('operation', operation);

            const response = await fetch('/xiaozhi/personal/upload/batch', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();
            progressBar.style.width = '100%';
            progressText.textContent = '处理完成!';

            if (data.success && data.results) {
                for (const [fileName, fileResult] of Object.entries(data.results)) {
                    if (typeof fileResult === 'object') {
                        results.push({
                            file: fileName,
                            success: fileResult.success,
                            message: fileResult.message || (fileResult.success ? '处理完成' : '处理失败'),
                            terms: fileResult.terms
                        });
                    } else {
                        results.push({
                            file: fileName,
                            success: true,
                            message: fileResult || '处理完成'
                        });
                    }
                }
            } else {
                selectedFiles.forEach((file) => {
                    results.push({
                        file: file.name,
                        success: false,
                        message: data.message || '处理失败'
                    });
                });
            }
        } catch (err) {
            progressBar.style.width = '100%';
            progressText.textContent = '处理完成!';
            selectedFiles.forEach((file) => {
                results.push({
                    file: file.name,
                    success: false,
                    message: '处理失败: ' + err.message
                });
            });
        }

        resultDiv.style.display = 'block';
        resultContent.textContent = JSON.stringify({
            operation: operation === 'TERMS' ? '术语解析' : '向量化存储',
            total: total,
            success: results.filter(r => r.success).length,
            failed: results.filter(r => !r.success).length,
            details: results
        }, null, 2);

        submitBtn.disabled = false;
        submitBtn.textContent = '开始上传';
      });
    }
  }

  function updateUploadSection() {
    const uploadSection = document.querySelector('#fileUploadSection');
    if (!uploadSection) return;

    const originalBtn = uploadSection.querySelector('.upload-btn:not(#batchUploadBtn)');
    const batchBtn = uploadSection.querySelector('#batchUploadBtn');

    if (originalBtn) {
      originalBtn.remove();
    }

    if (!batchBtn) {
      const btn = document.createElement('button');
      btn.id = 'batchUploadBtn';
      btn.className = 'upload-btn';
      btn.textContent = '批量上传文件';
      btn.addEventListener('click', () => {
        const modal = document.getElementById('batchModal');
        if (modal) modal.style.display = 'flex';
      });
      uploadSection.appendChild(btn);
    }

    const fileInput = uploadSection.querySelector('#fileInput');
    if (fileInput) {
      fileInput.remove();
    }
  }

  function initObserver() {
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.target.id === 'fileUploadSection') {
          updateUploadSection();
        }
      });
    });

    const uploadSection = document.querySelector('#fileUploadSection');
    if (uploadSection) {
      observer.observe(uploadSection, { childList: true, subtree: true });
    }
  }

  window.addEventListener('load', () => {
    setTimeout(() => {
      ensureBatchUI();
      updateUploadSection();
      initObserver();
    }, 100);
  });

  window.addEventListener('batch-upload-ready', ensureBatchUI);
})();
