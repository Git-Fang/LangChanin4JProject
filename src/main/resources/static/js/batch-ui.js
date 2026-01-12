(function(){
  function ensureBatchUI(){
    if (document.getElementById('batchUploadBtn')) return;
    const up = document.querySelector('.upload-section');
    if (up){
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
        background: 'rgba(0,0,0,.3)',
        zIndex: '1000'
      });
      modal.innerHTML = `
        <div style="width:520px; margin: 8% auto; background:#fff; padding:16px; border-radius:8px;">
          <h3>批量上传文件</h3>
          <input id="batchFileInput" type="file" multiple accept=".doc,.docx,.ppt,.pptx,.pdf,.txt,.md,.jpg,.jpeg,.png" />
          <div id="batchFileList" style="max-height:150px; overflow:auto; border:1px solid #eee; padding:6px; margin:6px 0;"></div>
          <div>
            <label>后续操作</label>
            <select id="batchAction" style="width:100%; padding:6px;">
              <option value="TERMS">术语解析</option>
              <option value="VECTORIZE" selected>向量化存储</option>
            </select>
          </div>
          <div style="text-align:right; margin-top:8px;">
            <button id="batchCancelBtn" class="upload-btn" style="background:#eee; color:#333;">取消</button>
            <button id="batchSubmitBtn" class="upload-btn" style="margin-left:8px;">开始上传</button>
          </div>
          <div id="batchResult" style="display:none;">
            <h4>批量结果</h4>
            <pre id="batchResultContent" style="max-height:200px; overflow:auto; background:#f8f9fa; padding:8px; border:1px solid #e0e0e0; border-radius:4px;"></pre>
          </div>
        </div>`;
      document.body.appendChild(modal);
      modal.querySelector('#batchCancelBtn').addEventListener('click', () => { modal.style.display = 'none'; });
      modal.querySelector('#batchSubmitBtn').addEventListener('click', async () => {
        const input = modal.querySelector('#batchFileInput');
        const files = input.files;
        if (!files || files.length === 0) { alert('请至少选择一个文件'); return; }
        const fd = new FormData();
        Array.from(files).forEach(f => fd.append('files', f));
        fd.append('operation', modal.querySelector('#batchAction').value);
        try {
          const resp = await fetch('/xiaozhi/personal/upload/batch', { method: 'POST', body: fd });
          const data = await resp.json();
          const batchResult = modal.querySelector('#batchResult');
          batchResult.style.display = 'block';
          batchResult.querySelector('#batchResultContent').textContent = JSON.stringify(data, null, 2);
        } catch (err) {
          alert('批量上传请求失败: ' + err.message);
        }
      });
      input.addEventListener('change', (e) => {
        const list = modal.querySelector('#batchFileList');
        list.innerHTML = '';
        const files = Array.from(e.target.files).slice(0,5);
        files.forEach((f)=>{
          const div = document.createElement('div');
          div.textContent = f.name;
          list.appendChild(div);
        });
      });
    }
  }
  window.addEventListener('load', () => { ensureBatchUI(); });
})();
