require './draminate'
require 'twitter'

client = Twitter::REST::Client.new do |config|
  config.consumer_key        = ENV['TWITTER_CONSUMER_KEY']
  config.consumer_secret     = ENV['TWITTER_CONSUMER_SECRET']
  config.access_token        = ENV['TWITTER_ACCESS_TOKEN']
  config.access_token_secret = ENV['TWITTER_ACCESS_SECRET']
end

seed = Random.new_seed
Random.srand(seed)
drama = draminate

tweet = "#{drama} https://ftb-drama.herokuapp.com/#{current_version}/#{seed.to_s(36)}"

client.update(tweet)
