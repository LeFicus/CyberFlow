import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'crawler-service/app/crawler/ecommerce_spider'))
from ecommerce_spider.normalization import usable_product_images, adjusted_price_usd
import test_product_crawl_phase1 as phase1
from scrapy.exceptions import DropItem
from ecommerce_spider.crawl_options import CrawlOptions

class ImagePolicyTests(unittest.TestCase):
    def test_empty_and_known_defaults_are_rejected(self):
        for value in [None, '', '[]', 'null', '<img>', 'data:image/gif;base64,abc', 'https://cdn.test/ProductDefault.gif', 'https://cdn.test/woocommerce-placeholder.png', 'https://cdn.test/no_image_400.jpg', 'https://cdn.test/image-not-found.svg','https://cdn.test/default-product.png','https://cdn.test/logo.png']:
            with self.subTest(value=value): self.assertEqual(usable_product_images(value),'')
        self.assertEqual(usable_product_images('https://cdn.test/Hitachi_Spares_Logo__123.png','mcquillantools.ie'),'')
    def test_mixed_arrays_urls_and_cdn_commas_are_preserved(self):
        actual='https://cdn.test/image/upload/w_300,h_200/real-hammer.jpg?color=red,blue'
        self.assertEqual(usable_product_images(f'https://cdn.test/ProductDefault.gif, {actual}'),actual)
        self.assertEqual(usable_product_images(['//cdn.test/a.jpg', '//cdn.test/a.jpg', 'https://cdn.test/placeholder.jpg']), 'https://cdn.test/a.jpg')
        self.assertEqual(usable_product_images('["https://cdn.test/a.jpg"]'),'https://cdn.test/a.jpg')
        self.assertEqual(usable_product_images('https://cdn.test/black-hammer.jpg'),'https://cdn.test/black-hammer.jpg')
    def test_price_boundary_range_and_stability(self):
        for price in [0,12.34,120,149.99,150]: self.assertEqual(adjusted_price_usd(price,'shop.test','A'),price)
        for i in range(10000):
            price=adjusted_price_usd(151,'shop.test',str(i))
            self.assertGreaterEqual(price,120);self.assertLessEqual(price,150)
            self.assertEqual(price,adjusted_price_usd(999,'shop.test',str(i)))
        self.assertEqual(adjusted_price_usd(151,'SHOP.TEST',' A '),adjusted_price_usd(999,'shop.test','A'))

class PipelinePolicyTests(unittest.TestCase):
    setUp = phase1.PipelineTests.setUp
    def test_price_is_converted_then_adjusted_and_original_is_retained(self):
        item=phase1.product(); item.update({'Regular price':1000,'货币':'AUD'})
        self.pipeline.process_item(item,self.spider)
        self.pipeline.close_spider(self.spider)
        values=self.database.rows[('shop.test','A')]
        self.assertTrue(120<=values[3]<=150)
        self.assertEqual(values[-1],670)
    def test_no_image_cannot_bypass_quality_gate(self):
        self.pipeline.options=CrawlOptions(require_image=False)
        for value in ['', 'https://cdn.test/ProductDefault.gif']:
            item=phase1.product();item['Images']=value
            with self.assertRaises(DropItem): self.pipeline.process_item(item,self.spider)
        self.assertEqual(self.pipeline.metrics.counts['accepted'],0)
