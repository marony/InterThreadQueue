# スレッド間のキューのパフォーマンステスト

## 説明

　Java標準のBlokingQueueとLMAX-Exchangeという取引所？で使用されているらしいDisruptorというライブラリでのスレッド間でのメッセージ受け渡しのパフォーマンスを測ってみました。
　今回は、送信1スレッド:受信1スレッドでのテストです。

[Disruptor by LMAX-Exchange](https://lmax-exchange.github.io/disruptor/ "Disruptor by LMAX-Exchange")

　スレッド間のメッセージの受け渡し回数2^23(N = 8388608)
　※ Disruptorのリングバッファのサイズが2の乗数しか指定できないため

BlockingQueueでの結果:
3888 millis

Disruptorでの結果:
785 millis

Disruptor速いですね！！
