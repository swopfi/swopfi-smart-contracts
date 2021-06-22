import timeit
import requests
import time
import json

matcher_url = "https://matcher.waves.exchange"

def extract_matcher_orderbook(url, timeout):
    status_code = 0
    resp = 0
    while status_code != 200:
        time.sleep(0.01)
        response = requests.get(url)
        status_code = response.status_code
        best_bid_price = json.loads(str(response.content.decode('utf8')))["bids"][0]["price"]
        best_ask_price = json.loads(str(response.content.decode('utf8')))["asks"][0]["price"]
    return best_bid_price,best_ask_price

while 1:
    pair = "WAVES/DG2xFkPdDwKUoBkzGAhQtLpSGzfXLiCYPEzeKH2Ad24p"
    start = timeit.default_timer()
    price_asset_decimals = 6
    best_bid_price,best_ask_price = extract_matcher_orderbook(matcher_url + "/matcher/orderbook/" + pair,100)
    
    stop = timeit.default_timer()
    execution_time = stop - start
    print("ask",best_ask_price/10**price_asset_decimals,"| bid",best_bid_price/10**price_asset_decimals, "| extract time",execution_time)
