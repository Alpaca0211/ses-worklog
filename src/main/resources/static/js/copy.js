// 週報システムへ貼り付けるためのコピー補助。
// 生成物はあくまで下書きで、貼り付けと提出は利用者が行う。
//
// インライン onclick を使わずイベント委譲にしているのは、Thymeleaf が
// イベントハンドラ属性への変数展開を拒否するため（テキストの混入を防ぐ仕様）。
document.addEventListener('click', function (event) {
    var button = event.target.closest('.copy-btn');
    if (!button) {
        return;
    }
    var source = document.getElementById(button.dataset.target);
    if (!source) {
        return;
    }
    var original = button.textContent;
    navigator.clipboard.writeText(source.textContent).then(function () {
        button.textContent = 'コピーしました';
        setTimeout(function () { button.textContent = original; }, 1500);
    }, function () {
        button.textContent = 'コピーできません';
        setTimeout(function () { button.textContent = original; }, 1500);
    });
});
